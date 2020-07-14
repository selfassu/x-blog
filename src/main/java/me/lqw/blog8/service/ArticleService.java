package me.lqw.blog8.service;

import me.lqw.blog8.BlogContext;
import me.lqw.blog8.exception.LogicException;
import me.lqw.blog8.mapper.*;
import me.lqw.blog8.model.*;
import me.lqw.blog8.model.dto.PageResult;
import me.lqw.blog8.model.vo.ArticleQueryParam;
import me.lqw.blog8.model.vo.HandledArticleQueryParam;
import me.lqw.blog8.util.JacksonUtil;
import me.lqw.blog8.util.JsoupUtils;
import me.lqw.blog8.validator.StatusEnum;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.io.Closeable;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class ArticleService extends BaseService<Article> implements CommentModuleHandler<Article>, InitializingBean {

    private final ArticleMapper articleMapper;
    private final CategoryMapper categoryMapper;
    private final ArticleTagMapper articleTagMapper;
    private final TagMapper tagMapper;
    private final MarkdownHandler mdHandler;
    private final CommentMapper commentMapper;
//    private final ArticleIndexer articleIndexer;

    public ArticleService(ArticleMapper articleMapper, CategoryMapper categoryMapper,
                          ArticleTagMapper articleTagMapper, TagMapper tagMapper,
                          MarkdownHandler mdHandler, CommentMapper commentMapper) throws IOException {
        this.articleMapper = articleMapper;
        this.categoryMapper = categoryMapper;
        this.articleTagMapper = articleTagMapper;
        this.tagMapper = tagMapper;
        this.mdHandler = mdHandler;
        this.commentMapper = commentMapper;
//        articleIndexer = new ArticleIndexer();
    }


    @Transactional(propagation = Propagation.REQUIRED)
    @Override
    public Article save(Article article) throws LogicException {
        logger.info("articleService save() [{}]", JacksonUtil.toJsonString(article));

        String urlName = article.getUrlName();
        if(!StringUtils.isEmpty(urlName)){
            if(urlName.contains("/")){
                urlName = urlName.replace("/", "");
            }
            articleMapper.findByUrlName(urlName).ifPresent(e -> {
                throw new LogicException("articleService.save.aliasExists", "别名已经存在");
            });
        }

        Category category = article.getCategory();
        categoryMapper.findById(category.getId()).orElseThrow(() ->
                new LogicException("articleService.save.categoryNotExists", "分类不存在"));

        StatusEnum status = article.getStatus();
        switch (status){
            case DRAFT:
                article.setPostAt(null);
                break;
            case POSTED:
                article.setPostAt(LocalDateTime.now());
            default:
                break;
        }
        //判断是否为定时发送
        if(status.equals(StatusEnum.SCHEDULED)){
//            ExecutorService executorService = Executors.newSingleThreadScheduledExecutor();
//            executorService.submit(new RunT)
        }
        article.setHits(0);
        article.setComments(0);

        articleMapper.insert(article);

        handleArticleTags(article);

        return article;
    }

    @Override
    public void delete(Integer id) throws LogicException {
        Article article = articleMapper.findById(id).orElseThrow(()
                -> new LogicException("articleService.delete.notExists", "内容不存在"));
        articleMapper.delete(article.getId());
        articleTagMapper.deleteByArticle(article);
        commentMapper.deleteByModule(new CommentModule(article.getId(), getModuleName()));

    }

    private void handleArticleTags(Article article) {
        articleTagMapper.deleteByArticle(article);
        Set<Tag> tags = article.getTags();
        if(!tags.isEmpty()){
            List<ArticleTag> articleTags = new ArrayList<>(tags.size());
            tags.forEach(e -> {
                Optional<Tag> tagOp = tagMapper.findByName(e.getTagName());
                if(tagOp.isPresent()){
                    articleTags.add(new ArticleTag(article, tagOp.get()));
                } else {
                    Tag tag = new Tag();
                    tag.setTagName(e.getTagName());
                    tagMapper.insert(tag);
                    articleTags.add(new ArticleTag(article, tag));
                }
            });
            articleTagMapper.batchInsert(articleTags);
        }
    }


    @Transactional(readOnly = true)
    public PageResult<Article> selectPage(ArticleQueryParam queryParam) {
        String category = queryParam.getCategory();

        String tag = queryParam.getTag();

        Integer categoryId = null;
        Integer tagId = null;
        if(!StringUtils.isEmpty(category)){
            Optional<Category> categoryOptional = categoryMapper.findByName(category);
            if(categoryOptional.isPresent()){
                categoryId = categoryOptional.get().getId();
            }
            if(categoryId == null){
                return new PageResult<>(queryParam, 0, Collections.emptyList());
            }
        }

        if(!StringUtils.isEmpty(tag)){
            Optional<Tag> byName = tagMapper.findByName(tag);
            if(byName.isPresent()){
                tagId = byName.get().getId();
            }
            if(tagId == null){
                return new PageResult<>(queryParam, 0, Collections.emptyList());
            }
        }



        HandledArticleQueryParam handledArticleQueryParam = new HandledArticleQueryParam(queryParam, categoryId, tagId);

        int count = articleMapper.count(handledArticleQueryParam);

        if(count == 0){
            return new PageResult<>(queryParam, 0, Collections.emptyList());
        }

        List<Article> articles = articleMapper.selectPage(handledArticleQueryParam);

        handleContentAndDigestData(articles);


        return new PageResult<>(queryParam, count, articles);

    }

    @Transactional(readOnly = true)
    public PageResult<ArticleArchive> selectArchivePage(ArticleQueryParam queryParam){

        int count = articleMapper.countArchive(queryParam);

        if(count == 0){
            return new PageResult<>(queryParam, 0, Collections.emptyList());
        }

        List<ArticleArchive> data = articleMapper.selectArchivePage(queryParam);

        if(data == null || data.isEmpty()){
            return new PageResult<>(queryParam, 0, Collections.emptyList());
        }

        for(ArticleArchive archive : data){
            List<Article> articles = archive.getArticles();
            handleContentAndDigestData(articles);
        }

        return new PageResult<>(queryParam, count, data);

    }


    private void handleContentAndDigestData(List<Article> articles){
        articles.forEach(article -> {
            String digest = article.getDigest();
            if(!StringUtils.isEmpty(digest)){
                article.setDigest(mdHandler.toHtml(digest));
            }

            String content = article.getContent();
            String featureImage = article.getFeatureImage();
            if(StringUtils.isEmpty(featureImage)){
                article.setFeatureImage(JsoupUtils.getFirstImage(mdHandler.toHtml(content)).orElse(""));
            }
            article.setContent(null);
        });
    }


    @Transactional(propagation = Propagation.REQUIRED)
    public void hit(int id) throws LogicException {
        if(BlogContext.isAuthorized()){
            return;
        }
        Article article = articleMapper.findById(id).orElseThrow(()
                -> new LogicException("articleService.hit.notExists", "内容不存在"));
        StatusEnum status = article.getStatus();
        if(!StatusEnum.POSTED.equals(status)){
            throw new LogicException("articleService.hit.notAllowed", "不允许操作");
        }
        articleMapper.increaseHits(id, 1);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public Optional<Article> getArticleForEdit(int id) {
        return articleMapper.findById(id);
    }


    @Transactional(readOnly = true)
//    @Cacheable(value = "article", key = "#idOrUrlName")
    public Optional<Article> get(String idOrUrlName) {
        Optional<Article> articleOp;
        try {
            Integer id = Integer.parseInt(idOrUrlName);
            articleOp = articleMapper.findById(id);
        } catch (NumberFormatException e){
            articleOp = articleMapper.findByUrlName(idOrUrlName);
        }
        if(articleOp.isPresent()){
            StatusEnum status = articleOp.get().getStatus();
            if(!StatusEnum.POSTED.equals(status) && BlogContext.isAuthorized()){
                return Optional.empty();
            }
            handleArticles(Collections.singletonList(articleOp.get()), BlogContext.isAuthorized());
//            System.out.println("为 id、key 为的 article 做了缓存");
            return articleOp;
        }
        return Optional.empty();
    }

    private void handleArticles(List<Article> articles, Boolean auth) {
        for(Article article: articles){
            String content = article.getContent();
            article.setContent(mdHandler.toHtml(content));
        }
    }

    @Override
    public String getModuleName() {
        return Article.class.getSimpleName().toLowerCase();
    }

    @Override
    public Article checkBeforeQuery(CommentModule commentModule) {
        return null;
    }

    @Override
    public void checkBeforeSaved(Comment comment, CommentModule commentModule) {

    }

    @Override
    public void increaseComments(CommentModule module) throws LogicException {
        articleMapper.findById(module.getId()).orElseThrow(() -> new LogicException("articleService.increaseComments.notExists", "内容不存在"));
        articleMapper.increaseComments(module.getId());
    }


    @Transactional(propagation = Propagation.REQUIRED)
    public void update(Article article) throws LogicException {
        Article old = articleMapper.findById(article.getId()).orElseThrow(() ->
                new LogicException("articleService.update.notExists", "内容不存在"));

    }


    @Override
    public void afterPropertiesSet() throws Exception {
        logger.info("ArticleService afterPropertiesSet()...");
    }



    public static final class ScheduleManager implements Closeable {

        private LocalDateTime start;

        private PlatformTransactionManager transactionManager;
        private TransactionTemplate writeTransactionTemplate;

        private final ExecutorService es = Executors.newSingleThreadScheduledExecutor();

        public ScheduleManager(PlatformTransactionManager transactionManager) {
            this.transactionManager = transactionManager;
//            this.writeTransactionTemplate = transactionManager.
        }

        private void post(){

            if(start == null || start.isAfter(LocalDateTime.now())){
                return;
            }

        }

        @Override
        public void close() throws IOException {

        }
    }
}
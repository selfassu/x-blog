package me.lqw.blog8.model;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.List;

/**
 * 动态归档
 * @author liqiwen
 * @version 1.0
 */
public class MomentArchive implements Serializable {

    private LocalDate date;

    private List<Moment> moments;

    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

    public List<Moment> getMoments() {
        return moments;
    }

    public void setMoments(List<Moment> moments) {
        this.moments = moments;
    }
}

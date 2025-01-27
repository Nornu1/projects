package com.bot.bot.bot;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UserScore {
    private String userName;
    private int score;
    private boolean hasCompletedQuiz;

    public UserScore(String userName, int score, boolean status) {
        this.userName = userName;
        this.score = score;
        this.hasCompletedQuiz= status;
    }

}

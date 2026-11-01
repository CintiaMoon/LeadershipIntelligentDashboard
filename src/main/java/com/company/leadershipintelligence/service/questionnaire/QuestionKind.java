package com.company.leadershipintelligence.service.questionnaire;

/** Question types of the builder (decision D47). Each maps to one engine question type, except free text, which is never scored. */
public enum QuestionKind {
    SINGLE_CHOICE,
    MULTI_CHOICE,
    RATING,
    LIKERT,
    NPS,
    NUMERIC,
    YES_NO,
    FREE_TEXT;

    public boolean choice() {
        return this == SINGLE_CHOICE || this == MULTI_CHOICE;
    }
}

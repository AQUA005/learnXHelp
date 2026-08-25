package com.ustc.learnx.service;

import com.ustc.learnx.entity.ExamQuestion;
import com.ustc.learnx.service.GradingService.GradingResult;
import com.ustc.learnx.service.GradingService.SubmittedAnswer;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Grading decides students' marks, so the edge cases are pinned down here:
 * blank answers, unknown question ids, missing answer keys, and duplicates.
 */
class GradingServiceTest {

    private final GradingService grading = new GradingService();

    private static ExamQuestion mcq(long id, int points, String correctAnswer) {
        return ExamQuestion.builder()
                .id(id)
                .questionText("Question " + id)
                .questionType(ExamQuestion.QuestionType.MCQ)
                .points(points)
                .correctAnswer(correctAnswer)
                .build();
    }

    private static ExamQuestion shortAnswer(long id, int points, String correctAnswer) {
        return ExamQuestion.builder()
                .id(id)
                .questionText("Question " + id)
                .questionType(ExamQuestion.QuestionType.SHORT_ANSWER)
                .points(points)
                .correctAnswer(correctAnswer)
                .build();
    }

    @Test
    void awardsPointsForEachCorrectAnswer() {
        GradingResult result = grading.grade(
                List.of(mcq(1, 5, "A"), mcq(2, 3, "B")),
                List.of(new SubmittedAnswer(1L, "A"), new SubmittedAnswer(2L, "B")));

        assertThat(result.score()).isEqualTo(8);
        assertThat(result.maxMarks()).isEqualTo(8);
    }

    @Test
    void awardsNothingForWrongAnswers() {
        GradingResult result = grading.grade(
                List.of(mcq(1, 5, "A")),
                List.of(new SubmittedAnswer(1L, "B")));

        assertThat(result.score()).isZero();
    }

    @Test
    void matchesIgnoringCaseAndSurroundingSpace() {
        GradingResult result = grading.grade(
                List.of(shortAnswer(1, 4, "Normalization")),
                List.of(new SubmittedAnswer(1L, "  normalIZATION  ")));

        assertThat(result.score()).isEqualTo(4);
    }

    /** Unanswered questions still count towards the total available marks. */
    @Test
    void maxMarksCoversUnansweredQuestions() {
        GradingResult result = grading.grade(
                List.of(mcq(1, 5, "A"), mcq(2, 7, "B")),
                List.of(new SubmittedAnswer(1L, "A")));

        assertThat(result.score()).isEqualTo(5);
        assertThat(result.maxMarks()).isEqualTo(12);
    }

    /** A client must not be able to earn marks for questions on another exam. */
    @Test
    void ignoresAnswersToQuestionsNotOnThisExam() {
        GradingResult result = grading.grade(
                List.of(mcq(1, 5, "A")),
                List.of(new SubmittedAnswer(1L, "A"), new SubmittedAnswer(999L, "A")));

        assertThat(result.score()).isEqualTo(5);
        assertThat(result.maxMarks()).isEqualTo(5);
    }

    /** Repeating a question must not pay twice. */
    @Test
    void gradesEachQuestionOnlyOnce() {
        GradingResult result = grading.grade(
                List.of(mcq(1, 5, "A")),
                List.of(new SubmittedAnswer(1L, "A"), new SubmittedAnswer(1L, "A")));

        assertThat(result.score()).isEqualTo(5);
    }

    /** The first answer for a question is the one that counts. */
    @Test
    void keepsTheFirstAnswerWhenAQuestionIsAnsweredTwice() {
        GradingResult result = grading.grade(
                List.of(mcq(1, 5, "A")),
                List.of(new SubmittedAnswer(1L, "WRONG"), new SubmittedAnswer(1L, "A")));

        assertThat(result.score()).isZero();
    }

    /**
     * A question saved without an answer key cannot be marked automatically.
     * It must score zero rather than throwing and losing the whole submission.
     */
    @Test
    void scoresZeroForAQuestionWithNoAnswerKey() {
        GradingResult result = grading.grade(
                List.of(shortAnswer(1, 5, null)),
                List.of(new SubmittedAnswer(1L, "anything")));

        assertThat(result.score()).isZero();
        assertThat(result.maxMarks()).isEqualTo(5);
    }

    @Test
    void handlesBlankAndMissingAnswers() {
        GradingResult result = grading.grade(
                List.of(mcq(1, 5, "A"), mcq(2, 5, "B")),
                java.util.Arrays.asList(
                        new SubmittedAnswer(1L, null),
                        new SubmittedAnswer(null, "B"),
                        null));

        assertThat(result.score()).isZero();
        assertThat(result.maxMarks()).isEqualTo(10);
    }

    @Test
    void handlesAnEmptySubmission() {
        GradingResult result = grading.grade(List.of(mcq(1, 5, "A")), List.of());

        assertThat(result.score()).isZero();
        assertThat(result.maxMarks()).isEqualTo(5);
    }

    @Test
    void handlesAnExamWithNoQuestions() {
        GradingResult result = grading.grade(List.of(), List.of(new SubmittedAnswer(1L, "A")));

        assertThat(result.score()).isZero();
        assertThat(result.maxMarks()).isZero();
    }
}

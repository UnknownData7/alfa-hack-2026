package ru.hackathon.credit.application;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Допустимые и запрещённые переходы машины состояний. */
class ApplicationStatusTest {

    @Test
    void allowedTransitions() {
        assertTrue(ApplicationStatus.DRAFT.canMoveTo(ApplicationStatus.SUBMITTED));
        assertTrue(ApplicationStatus.SUBMITTED.canMoveTo(ApplicationStatus.NEED_INFO));
        assertTrue(ApplicationStatus.NEED_INFO.canMoveTo(ApplicationStatus.SUBMITTED));
        assertTrue(ApplicationStatus.SUBMITTED.canMoveTo(ApplicationStatus.SCORING));
        assertTrue(ApplicationStatus.SCORING.canMoveTo(ApplicationStatus.OFFER_MADE));
        assertTrue(ApplicationStatus.SCORING.canMoveTo(ApplicationStatus.DECLINED));
        assertTrue(ApplicationStatus.SCORING.canMoveTo(ApplicationStatus.MANUAL_REVIEW));
        assertTrue(ApplicationStatus.MANUAL_REVIEW.canMoveTo(ApplicationStatus.MANUAL_REVIEW));
        assertTrue(ApplicationStatus.MANUAL_REVIEW.canMoveTo(ApplicationStatus.NEED_INFO));
        assertTrue(ApplicationStatus.MANUAL_REVIEW.canMoveTo(ApplicationStatus.OFFER_MADE));
        assertTrue(ApplicationStatus.MANUAL_REVIEW.canMoveTo(ApplicationStatus.DECLINED));
        assertTrue(ApplicationStatus.OFFER_MADE.canMoveTo(ApplicationStatus.SIGNING));
        assertTrue(ApplicationStatus.OFFER_MADE.canMoveTo(ApplicationStatus.CLOSED));
        assertTrue(ApplicationStatus.SIGNING.canMoveTo(ApplicationStatus.ISSUED));
    }

    @Test
    void forbiddenTransitions() {
        assertFalse(ApplicationStatus.DRAFT.canMoveTo(ApplicationStatus.OFFER_MADE));
        assertFalse(ApplicationStatus.SUBMITTED.canMoveTo(ApplicationStatus.MANUAL_REVIEW));
        assertFalse(ApplicationStatus.SCORING.canMoveTo(ApplicationStatus.SIGNING));
        assertFalse(ApplicationStatus.OFFER_MADE.canMoveTo(ApplicationStatus.ISSUED));
        assertFalse(ApplicationStatus.DECLINED.canMoveTo(ApplicationStatus.SUBMITTED));
        assertFalse(ApplicationStatus.ISSUED.canMoveTo(ApplicationStatus.SIGNING));
        assertFalse(ApplicationStatus.CLOSED.canMoveTo(ApplicationStatus.OFFER_MADE));
    }

    @Test
    void finalStatuses() {
        assertTrue(ApplicationStatus.ISSUED.isFinal());
        assertTrue(ApplicationStatus.DECLINED.isFinal());
        assertTrue(ApplicationStatus.CLOSED.isFinal());
        assertFalse(ApplicationStatus.MANUAL_REVIEW.isFinal());
    }
}

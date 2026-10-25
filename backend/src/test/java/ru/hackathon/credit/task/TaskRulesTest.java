package ru.hackathon.credit.task;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class TaskRulesTest {

    @Test
    void priorityRules() {
        assertEquals(1, TaskRules.priority(6_000_000, true));
        assertEquals(2, TaskRules.priority(5_000_000, false));
        assertEquals(5, TaskRules.priority(800_000, false));
        assertEquals(5, TaskRules.priority(500_000, false));
        assertEquals(7, TaskRules.priority(499_999, false));
    }

    @Test
    void checksDependOnApplication() {
        List<String> collateral = codes(new TaskRules.CheckContext(true, 0.2, true, 0, 40, "MEDIUM"));
        assertEquals(List.of("COLLATERAL"), collateral);

        List<String> young = codes(new TaskRules.CheckContext(false, 0.19, false, 0, 9, "HIGH"));
        assertTrue(young.contains("TURNOVER_CONFIRMATION"));
        assertTrue(young.contains("BUSINESS_ACTIVITY"));

        List<String> history = codes(new TaskRules.CheckContext(false, 0.1, true, 20, 40, "LOW"));
        assertEquals(List.of("CREDIT_HISTORY"), history);

        List<String> general = codes(new TaskRules.CheckContext(false, 0.1, true, 0, 40, "LOW"));
        assertEquals(List.of("GENERAL_REVIEW"), general);
    }

    private static List<String> codes(TaskRules.CheckContext ctx) {
        return TaskRules.checks(ctx).stream().map(TaskRules.CheckDefinition::code).toList();
    }
}

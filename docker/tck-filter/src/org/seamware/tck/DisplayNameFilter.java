package org.seamware.tck;

import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * JUnit Jupiter extension that filters tests by display name and registers
 * EDC-specific JSON-LD contexts with the TCK.
 *
 * <p>Set the system property {@code tck.filter} to a substring that must appear
 * in the test method or test class display name. Tests that don't match are
 * disabled. If the property is empty or unset, all tests run.
 *
 * <p>Test containers (engines, classes) are always enabled so their children
 * can be evaluated individually.
 *
 * <p>Usage: {@code java -Dtck.filter=TP:03 -cp tck-runtime.jar:tck-filter.jar ...}
 */
public class DisplayNameFilter implements ExecutionCondition {

    private static final String FILTER_PROPERTY = "tck.filter";

    static {
        // Register EDC-specific JSON-LD contexts so the TCK can process
        // EDC responses without fetching remote context documents.
        EdcContextRegistrar.register();
    }

    @Override
    public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
        var filter = System.getProperty(FILTER_PROPERTY, "");
        if (filter.isEmpty()) {
            return ConditionEvaluationResult.enabled("No filter set");
        }

        // Only filter at the test method level; always enable containers
        // (engines, classes) so their children can be evaluated.
        if (context.getTestMethod().isEmpty()) {
            return ConditionEvaluationResult.enabled("Container — not filtered");
        }

        var displayName = context.getDisplayName();
        if (displayName.contains(filter)) {
            return ConditionEvaluationResult.enabled("Matches filter: " + filter);
        }

        // Also match against parent class display name for broader filters like "TP:03"
        var parentMatch = context.getParent()
                .map(ExtensionContext::getDisplayName)
                .map(name -> name.contains(filter))
                .orElse(false);
        if (parentMatch) {
            return ConditionEvaluationResult.enabled("Parent matches filter: " + filter);
        }

        return ConditionEvaluationResult.disabled("Does not match filter: " + filter);
    }
}

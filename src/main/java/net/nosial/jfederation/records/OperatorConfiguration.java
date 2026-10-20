package net.nosial.jfederation.records;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * The settings of an operator (OFD 2.0). A host only discloses them to the operator itself and to operators with
 * operator permissions.
 *
 * @param autoAssignReports whether the operator opts in to the automatic assignment of reports that are not automated
 * @param autoAssignAutomatedReports whether the operator opts in to the automatic assignment of automated reports
 * @param isBot whether the operator is a bot, whose evidence and reports are automated and whose evidence is created
 *              confidential
 */
public record OperatorConfiguration(
    @JsonProperty("auto_assign_reports") boolean autoAssignReports,
    @JsonProperty("auto_assign_automated_reports") boolean autoAssignAutomatedReports,
    @JsonProperty("is_bot") boolean isBot
) { }

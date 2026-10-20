package net.nosial.jfederation.records;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Immutable record representing an operator (user) in the Federation server.
 *
 * @param uuid the unique operator identifier
 * @param name the display name of the operator
 * @param disabled whether the operator account is disabled
 * @param permissions the permission groups of the operator
 * @param configuration the settings of the operator, or {@code null} when the server did not disclose them; they are
 *                      only disclosed to the operator itself and to operators with operator permissions
 * @param created the creation timestamp (Unix epoch seconds)
 * @param updated the last-updated timestamp (Unix epoch seconds)
 */
public record OperatorRecord(
    @JsonProperty("uuid") String uuid,
    @JsonProperty("name") String name,
    @JsonProperty("disabled") boolean disabled,
    @JsonProperty("permissions") OperatorPermissions permissions,
    @JsonProperty("configuration") OperatorConfiguration configuration,
    @JsonProperty("created") long created,
    @JsonProperty("updated") long updated
) {
    /**
     * Compact constructor, an operator without permissions in the response holds none.
     */
    public OperatorRecord
    {
        if (permissions == null)
        {
            permissions = new OperatorPermissions(false, false, false);
        }
    }

    /**
     * Returns whether the operator holds client permissions.
     *
     * @return true if the operator holds client permissions
     */
    public boolean hasClientPermissions()
    {
        return permissions.client();
    }

    /**
     * Returns whether the operator holds management permissions.
     *
     * @return true if the operator holds management permissions
     */
    public boolean hasManagementPermissions()
    {
        return permissions.management();
    }

    /**
     * Returns whether the operator holds operator permissions.
     *
     * @return true if the operator holds operator permissions
     */
    public boolean hasOperatorPermissions()
    {
        return permissions.operator();
    }

    /**
     * Returns whether the server disclosed the configuration of the operator.
     *
     * @return true if {@link #configuration()} is not null
     */
    public boolean isConfigurationVisible()
    {
        return configuration != null;
    }

    /**
     * Returns whether the operator opts in to the automatic assignment of reports that are not automated.
     *
     * @return true if the operator opts in, false if it doesn't or the configuration was not disclosed
     */
    public boolean isAutoAssignReports()
    {
        return configuration != null && configuration.autoAssignReports();
    }

    /**
     * Returns whether the operator opts in to the automatic assignment of automated reports.
     *
     * @return true if the operator opts in, false if it doesn't or the configuration was not disclosed
     */
    public boolean isAutoAssignAutomatedReports()
    {
        return configuration != null && configuration.autoAssignAutomatedReports();
    }

    /**
     * Returns whether the operator opts in to the automatic assignment of any kind of report.
     *
     * @return true if the operator opts in to ordinary or automated reports
     */
    public boolean isAutoAssigned()
    {
        return isAutoAssignReports() || isAutoAssignAutomatedReports();
    }

    /**
     * Returns whether the operator is a bot.
     *
     * @return true if the operator is a bot, false if it isn't or the configuration was not disclosed
     */
    public boolean isBot()
    {
        return configuration != null && configuration.isBot();
    }
}

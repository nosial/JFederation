# JFederation

JFederation is a Java client library for the Federated Database protocol, providing a complete client-side implementation
of the standard for Java applications. The implementation is based on the
[Open Federated Database](https://github.com/nosial/OFD-Specification) specification, and the programming methodology is
heavily based on the original implementation, [FederationLib](https://github.com/nosial/FederationLib).

## Features

- Full implementation of the Federated Database standard, interacting with any OFD-compliant Federation server
- Content scanning with entity resolution, classification, and risk assessment
- Complete coverage of the server API: operators, records, evidence, reports, blacklist records, file
  attachments, audit logs, search, and server information
- Pagination, category filters, and sorting built into every listing and search endpoint
- Thread-safe client design, `FederationClient` instances can be shared across threads
- All errors are mapped to a single checked exception carrying the HTTP status code and server message

## Table of Contents

<!-- TOC -->
* [JFederation](#jfederation)
  * [Features](#features)
  * [Table of Contents](#table-of-contents)
  * [Prerequisites](#prerequisites)
  * [Installation](#installation)
    * [Via JitPack](#via-jitpack)
    * [From source](#from-source)
  * [Usage](#usage)
    * [Connecting to a server](#connecting-to-a-server)
    * [Content scanning](#content-scanning)
    * [Searching records](#searching-records)
    * [Operators](#operators)
    * [Entities](#entities)
    * [Evidence](#evidence)
    * [Reports](#reports)
    * [Blacklist records](#blacklist-records)
    * [File attachments](#file-attachments)
    * [Pagination and sorting](#pagination-and-sorting)
  * [Error handling](#error-handling)
  * [Testing](#testing)
* [License](#license)
<!-- TOC -->

## Prerequisites

- **Java 24 or newer** — the library is compiled with `--release 24`
- **Maven 3.8+** to build the project and run the test suite
- **A running Federation server** for anything other than constructing a client, the reference
  implementation is [FederationLib](https://github.com/nosial/FederationLib), which includes a
  dockerized server deployment

Dependencies are resolved from Maven Central: OkHttp for HTTP transport, Jackson for JSON serialization, and SLF4J for
logging (Logback is bundled as the runtime implementation).

## Installation

### Via JitPack

The project is hosted on [GitHub](https://github.com/nosial/JFederation), so Maven projects can pull
the library directly from the repository using [JitPack](https://jitpack.io/#nosial/JFederation),
JitPack compiles and caches the library automatically on the first resolution, no publishing is
required. This is useful for testing unreleased builds from the `master` branch.

Add the JitPack repository and the dependency to your `pom.xml`:

```xml
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>

<dependencies>
    <dependency>
        <groupId>com.github.nosial</groupId>
        <artifactId>JFederation</artifactId>
        <version>master-SNAPSHOT</version>
    </dependency>
</dependencies>
```

The `master-SNAPSHOT` version always resolves to the latest commit on the `master` branch. To pin a
specific version, tag the repository (for example `v1.0.0`) and use the tag name as the version.
### From source

To use the library without JitPack, build and install it into your local Maven repository:

```sh
mvn clean install
```

Then add the dependency to your project using the local coordinates:

```xml
<dependency>
    <groupId>net.nosial</groupId>
    <artifactId>jfederation</artifactId>
    <version>1.0.0</version>
</dependency>
```

## Usage

All functionality is exposed through a single entry point, the `FederationClient` class, every method
maps to a REST endpoint of the Federation server, and handles serialisation, authentication, and error
mapping internally.

### Connecting to a server

A client is constructed with the base URL of the Federation server and an optional operator access
token, clients without a token operate anonymously against the server's public endpoints:

```java
import net.nosial.jfederation.FederationClient;

// Anonymous client
FederationClient client = new FederationClient("https://federation.example.com");

// Authenticated client with an operator access token
FederationClient client = new FederationClient("https://federation.example.com", "access-token");
```

The access token can be changed at any time during the lifetime of the client:

```java
client.setAccessToken("new-token");   // switch authentication
client.setAccessToken(null);          // revert to anonymous access
```
ception`; authentication and authorisation errors bubble up with the 401/403
status codes of the response.
Clients implement `AutoCloseable` and should be closed when no longer needed to release the
underlying HTTP connection pool:

```java
client.close();
```

For custom HTTP settings (timeouts, proxies, TLS, etc.) an existing `OkHttpClient` can be passed to
the constructor instead:

```java
OkHttpClient httpClient = new OkHttpClient.Builder()
    .connectTimeout(15, TimeUnit.SECONDS)
    .readTimeout(15, TimeUnit.SECONDS)
    .build();

FederationClient client = new FederationClient("https://federation.example.com", "access-token", httpClient);
```

Server information, including the server name, API version, feature flags (which record types are
publicly visible), and total record counts, is available through `getServerInformation()`, the raw
OpenAPI specification of the server is available through `getSpecification()`:

```java
ServerInformation info = client.getServerInformation();
System.out.printf("Server %s, API version %s%n", info.serverName(), info.apiVersion());
System.out.printf("Known entities: %d, open reports: %d%n", info.knownEntities(), info.reports());
```

### Content scanning

Content scanners allow applications to submit text content to the server, the server resolves any
known entities found within the content, classifies the content, and returns a risk assessment:

| Method (omit optional parameters)                         | Return Type      | Description                                              |
|-----------------------------------------------------------|------------------|----------------------------------------------------------|
| `scanContent(content)`                                    | `ScannedContent` | Scans content with no additional parameters              |
| `scanContent(content, author)`                            | `ScannedContent` | Scans content with an optional author entity identifier  |
| `scanContent(content, author, topK)`                      | `ScannedContent` | Scans content with a maximum number of returned entities |
| `scanContent(content, author, topK, threshold)`           | `ScannedContent` | Scans content with the given confidence threshold        |
| `scanContent(content, author, topK, threshold, metadata)` | `ScannedContent` | Scans content with optional metadata                     |

Here's an example of scanning content and inspecting the classification result:

```java
ScannedContent scan = client.scanContent("Suspicious message to inspect", "author@example.com");

ContentClassification classification = scan.getClassification();
System.out.printf("Classification %s with %.1f%% confidence, detected language: %s%n",
    classification.classificationFlag(),
    classification.confidence() * 100.0,
    classification.detectedLanguage());
```

### Searching records

The global search queries all record types with an optional type filter, the search query must be at
least 2 characters:

```java
// Search all record types
List<SearchResult> results = client.search("author@example.com");

// Search only entity and blacklist records, page 1, 25 per page
List<SearchResult> results = client.search("author@example.com",
    List.of(RecordType.ENTITY, RecordType.BLACKLIST), 1, 25);
```

`SearchResult` records provide a typed `getRecord()` method that resolves the underlying record based
on the matched type.

### Operators

Operators are the accounts of the Federation server, all data-modifying functionality is performed
by an operator, and each operator carries granular permissions dictating what they are allowed to do.

| Method                                                                      | Description                                                                          |
|-----------------------------------------------------------------------------|--------------------------------------------------------------------------------------|
| `createOperator(name)`                                                      | Creates a new operator, returns the raw access token (only exposed at creation time) |
| `getOperator(uuid)` / `getSelf()`                                           | Returns an operator record / the currently authenticated operator                    |
| `listOperators(page, limit, category, by, order)`                           | Lists operators with optional filters                                                |
| `searchOperators(query, page, limit, category, by, order)`                  | Searches operators by name                                                           |
| `updateOperatorName(uuid, name)`                                            | Updates the display name of an operator                                              |
| `deleteOperator(uuid)`                                                      | Deletes an operator                                                                  |
| `disableOperator(uuid)` / `enableOperator(uuid)`                            | Disables / enables an operator account                                               |
| `setOperatorPermissions(uuid, enabled)`                                     | Sets whether the operator has operator-level permissions                             |
| `setClientPermissions(uuid, enabled)`                                       | Sets whether the operator has client-level permissions                               |
| `setManagementPermissions(uuid, enabled)`                                   | Sets whether the operator has management-level permissions                           |
| `generateAccessToken(update)`                                               | Refreshes the current operator's token, optionally updating the client               |
| `generateOperatorAccessToken(uuid)`                                         | Refreshes another operator's token                                                   |
| `listOperatorAuditLogs(uuid, ...)`                                          | Lists the audit log entries of an operator                                           |
| `listOperatorEvidence(uuid, ...)`                                           | Lists the evidence an operator submitted                                             |
| `listOperatorBlacklist(uuid, ...)`                                          | Lists the blacklist records created by an operator                                   |
| `listOperatorReports(uuid, ...)` / `listAssignedOperatorReports(uuid, ...)` | Lists the reports submitted by / assigned to an operator                             |

Here's an example of creating an operator and generating a new token:

```java
OperatorCreated created = client.createOperator("my-application");
System.out.println("Operator UUID: " + created.uuid());

// The raw token is only returned once at creation time, store it securely
String token = created.accessToken();

String refreshed = client.generateAccessToken(true); // refresh and update this client
```

### Entities

Entities are the subjects tracked by the federation; users, domains, IP addresses, or any other
identifier present in scanned content:

| Method                                                    | Description                                       |
|-----------------------------------------------------------|---------------------------------------------------|
| `getEntityRecord(identifier)`                             | Returns the record of an entity by identifier     |
| `searchEntities(query, page, limit, category, by, order)` | Searches entities by identifier                   |
| `listEntities(page, limit, category, by, order)`          | Lists entities                                    |
| `getTopThreats(limit)`                                    | Lists the highest-risk entities                   |
| `updateEntity(identifier, metadata)`                      | Updates the metadata of an entity                 |
| `deleteEntity(identifier)`                                | Deletes an entity                                 |
| `setEntityWhitelist(identifier, whitelisted)`             | (Un)whitelists an entity, exempting it from scans |
| `clearEntityReputation(identifier)`                       | Clears the accumulated reputation of an entity    |
| `setEntityRelationship(identifier, targetUuid, type)`     | Sets the relationship between two entities        |
| `clearEntityRelationship(identifier)`                     | Clears the relationship of an entity              |
| `pushEntity(host, identifier, metadata)`                  | Pushes an entity to another federation server     |
| `listEntityAuditLogs(identifier, ...)`                    | Lists the audit log entries involving an entity   |
| `listEntityBlacklistRecords(identifier, ...)`             | Lists the blacklist records against an entity     |
| `listEntityEvidenceRecords(identifier, ...)`              | Lists the evidence submitted against an entity    |
| `listEntityReports(identifier, ...)`                      | Lists the reports submitted against an entity     |

Record listings of entities are `EntityRecord` objects carrying the identifier, risk score,
classification flag, relationship hierarchy, and metadata of the entity.

### Evidence

Evidence records are the supporting data attached to entities, either when submitting a report or
manually by operators, and can hold text content, attachments, tags, notes, and confidentiality
flags:

| Method                                                                | Description                                    |
|-----------------------------------------------------------------------|------------------------------------------------|
| `submitEvidence(entityIdentifier)`                                    | Creates an empty evidence record for an entity |
| `submitEvidence(identifier, text, note, tag, confidential, metadata)` | Submits evidence with all optional fields      |
| `listEvidence(page, limit, includeConfidential, category, by, order)` | Lists evidence records                         |
| `searchEvidence(query, page, limit, category, by, order)`             | Searches evidence records                      |
| `getEvidenceRecord(uuid)`                                             | Returns a single evidence record               |
| `getEvidenceAttachments(uuid)`                                        | Lists the attachments of an evidence record    |
| `updateEvidenceConfidentiality(uuid, confidential)`                   | Updates the confidentiality flag of evidence   |
| `updateEvidenceTag(uuid, tag)`                                        | Updates the tag of evidence                    |
| `addEvidenceToReport(evidenceUuid, reportUuid)`                       | Attaches evidence to a report                  |
| `deleteEvidence(uuid)`                                                | Deletes an evidence record                     |

Confidential evidence is hidden from unauthenticated clients by default, listing endpoints
support an `includeConfidential` parameter to include them when the operator has the appropriate
permissions.

### Reports

Reports are the central mechanism of the federation server, submitted against an entity, they group
one or more pieces of evidence and can be classified, assigned to operators, and closed:

| Method                                                    | Description                                               |
|-----------------------------------------------------------|-----------------------------------------------------------|
| `submitReport(entity, content, incidentType, ...)`        | Submits a report against an entity for the given incident |
| `listReports(page, limit, category, by, order)`           | Lists reports                                             |
| `listOpenedReports(page, limit, by, order)`               | Lists reports that are not yet closed                     |
| `searchReports(query, page, limit, category, by, order)`  | Searches reports by content                               |
| `getReport(uuid)`                                         | Returns a single report                                   |
| `closeReport(uuid)` / `closeReport(uuid, classification)` | Closes a report, optionally with a classification         |
| `assignOperatorToReport(reportUuid, operatorUuid)`        | Assigns an operator to a report                           |
| `deleteReport(uuid)`                                      | Deletes a report                                          |

`submitReport` returns a `ReportSubmission` object which provides typed access to the created
report record (`getReport()`) and the evidence record automatically generated from the report's
content (`getEvidence()`), along with any attachment upload results the server generated.

### Blacklist records

Blacklist records are the server's mitigation mechanism, an operator can condemn an entity to the
blacklist of the server with an incident type and an evidence record backing the decision:

| Method                                                                  | Description                                                            |
|-------------------------------------------------------------------------|------------------------------------------------------------------------|
| `blacklistEntity(identifier, evidenceUuid, type)`                       | Blacklists an entity permanently                                       |
| `blacklistEntity(identifier, evidenceUuid, type, expires)`              | Blacklists an entity with an expiration timestamp (Unix epoch seconds) |
| `listBlacklistRecords(page, limit, includeLifted, category, by, order)` | Lists blacklist entries                                                |
| `searchBlacklist(query, page, limit, category, by, order)`              | Searches blacklist entries                                             |
| `getBlacklistRecord(uuid)`                                              | Returns a single blacklist record                                      |
| `liftBlacklistRecord(uuid)`                                             | Lifts the blacklist entry of an entity                                 |
| `extendBlacklistRecord(uuid, seconds)`                                  | Extends the blacklist of an entity by the given seconds                |
| `deleteBlacklistRecord(uuid)`                                           | Deletes a blacklist record                                             |

### File attachments

Evidence records support file attachments with uploads from disk or URL, and note attachments (plain-text content):

| Method                                                            | Description                                        |
|-------------------------------------------------------------------|----------------------------------------------------|
| `uploadFileAttachment(evidenceUuid, localFilePath)`               | Uploads a local file attachment                    |
| `uploadFileAttachment(evidenceUuid, localFilePath, fileName)`     | Uploads a local file attachment with a custom name |
| `uploadFileAttachmentFromUrl(evidenceUuid, fileUrl)`              | Uploads an attachment from a reachable URL         |
| `uploadFileAttachmentFromUrl(evidenceUuid, fileUrl, maxFileSize)` | Uploads an attachment from a URL with a size limit |
| `uploadNoteAttachment(evidenceUuid, fileName, content)`           | Uploads a plain-text note attachment               |
| `downloadAttachment(attachmentUuid, directoryPath)`               | Downloads an attachment into the given directory   |
| `getAttachmentInfo(uuid)`                                         | Returns the metadata of an attachment              |
| `listAttachments(page, limit, category, by, order)`               | Lists attachments                                  |
| `searchAttachments(query, page, limit, category, by, order)`      | Searches attachments                               |
| `deleteAttachment(uuid)`                                          | Deletes an attachment                              |

File uploads default to a maximum file size of 50 MB, matching the FederationLib client default, the
limit can be raised per-request for URL uploads.

### Pagination and sorting

Every listing and search endpoint accepts the same pagination and sorting arguments: `page` (1-based)
and `limit` (minimum 1), followed by an optional category filter, `by` (whatever field to sort by,
case-insensitive), and `order` (`"ASC"` or `"DESC"`). Every combination has a matching overload,
so the calling convention of each method grows gradually, for example:

```java
// Defaults: page 1, limit 100
List<EntityRecord> entities = client.listEntities();

// Explicit pagination
List<EntityRecord> entities = client.listEntities(2, 50);

// Pagination with a category filter, sorted by risk descending
List<EntityRecord> entities = client.listEntities(2, 50, "HIGH_RISK", "risk_score", "DESC");
```

None of the argument orderings change between endpoints, once the pattern is memorized the entire
API becomes predictable.

## Error handling

All failures are thrown as `FederationClientException`, carrying the HTTP status code of the
response and, when the server returns one, its error message:

```java
try
{
    client.getEntityRecord("non-existent-entity");
}
catch (FederationClientException e)
{
    System.out.printf("Request failed with status %d: %s%n", e.getStatusCode(), e.getMessage());
}
```

Invalid call parameters (empty content, tokens containing whitespace, queries shorter than 2
characters, zero page/limit values, etc.) are rejected by the client immediately with an
`IllegalArgumentException`; authentication and authorisation errors bubble up with the 401/403
status codes of the response.

## Testing

The test suite runs against a live Federation server, and the repository includes a
[docker-compose.yml](docker-compose.yml) that brings up the reference
[FederationLib](https://github.com/nosial/FederationLib) server, MariaDB,
and Redis on `http://localhost:7000`:

```sh
docker compose up -d
mvn test
```

By default the tests expect the server at `http://localhost:7000` with access token
`abcdefghijklmnopqrstuvwxyz123456` (matching the compose defaults). The test suite also can be
configured through the `SERVER_ENDPOINT` and `SERVER_ACCESS_TOKEN` environment for testing against
a different server:

```sh
SERVER_ENDPOINT=http://localhost:7000 SERVER_ACCESS_TOKEN=my-token mvn test
```

# License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.
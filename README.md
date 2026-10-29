# <picture><source media="(prefers-color-scheme: dark)" srcset="assets/icon/jfederation_light.svg"><img src="assets/icon/jfederation_dark.svg" alt="JFederation" width="32" height="32"></picture> JFederation

JFederation is a Java client library for the Federated Database protocol, providing a complete client-side implementation
of the standard for Java applications. The implementation is based on the
[Open Federated Database](https://github.com/nosial/OFD-Specification) specification, and the programming methodology is
heavily based on the original implementation, [FederationLib](https://github.com/nosial/FederationLib).

## Features

- Full implementation of the Federated Database standard (OFD 2.1), interacting with any OFD-compliant Federation server
- Content scanning with entity resolution, classification, risk assessment, content types, concerns, and detections
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
    <version>2.0.0</version>
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

A client should check the major version of the interface with `info.getApiMajorVersion()`, this release implements
OFD 2.1. The server information also publishes what content scanning supports (`scanContentTypes()`, `concerns()`,
`defaultConcerns()`, and the file limits `maxScanFiles()` and `maxScanFileSize()`), whether URL uploads are accepted
(`isUrlUploadsAllowed()`), whether telephone entities are public (`publicTelephoneEntities()`) and whether content
scanned anonymously may be stored (`collectsPublicScanContent()`). Values a newer server publishes that this client
does not recognize are skipped, and an unrecognized incident type is read as `OTHER`.

Routes the client has no method for, such as a route provided by a plugin of the server, are reached with
`request(method, path, parameters)`, authenticated with the client's access token if it has one. The parameters are
sent as the query string of a `GET` request and as a JSON body otherwise, and the response is returned as a `JsonNode`:

```java
JsonNode status = client.request("GET", "my-plugin/status", null);
```

### Content scanning

Content scanners accept a `ContentInput` or a list of inputs. Each input can carry text, a content type and its
context, an operator note, tag, confidentiality flag, and metadata; at least one input must contain text or files:

| Method (omit optional parameters)                                    | Return Type      | Description                                      |
|----------------------------------------------------------------------|------------------|--------------------------------------------------|
| `scanContent(content)`                                               | `ScannedContent` | Convenience overload for a single text input     |
| `scanContent(ContentInput, author, topK, threshold)`                 | `ScannedContent` | Scans one typed content input                    |
| `scanContent(List<ContentInput>, author, topK, threshold)`           | `ScannedContent` | Scans one or more typed content inputs           |
| `scanContent(List<ContentInput>, author, topK, threshold, concerns)` | `ScannedContent` | Scans with respect to the given incident types   |
| `scanContent(ContentInput, author, files)`                           | `ScannedContent` | Scans one input together with its files          |
| `scanContent(List<ContentInput>, …, concerns, files)`                | `ScannedContent` | Scans inputs with their files, keyed by index    |

The content type (`TEXT`, `EMAIL`, `SMS`, `WEB_PAGE`, `HOSTNAME` or `URL`) determines the form of the text and the
keys its context may contain, eg; `subject` and `sender` for an `EMAIL`, `region` for an `SMS`, and the required `url`
for a `WEB_PAGE`. Context is an assertion of the client, a detection whose `verified()` is false relies on it. Only the
requested concerns (the server's `defaultConcerns()` when none are given, which exclude `ADVERTISING` and `TRACKING`)
affect the classification, risk score, suggested action, and `detections()` of the result:

```java
ScannedContent scan = client.scanContent(List.of(
        new ContentInput("Your account is locked, verify it now", ContentType.EMAIL,
            Map.of("subject", "Account locked", "sender", "security@example.com")),
        new ContentInput("https://login.example.com/verify", ContentType.URL)),
    null, null, null, List.of(IncidentType.PHISHING, IncidentType.SPAM));

for (Detection detection : scan.detections()) {
    System.out.printf("%s in input %d by %s (verified: %b)%n",
        detection.concern(), detection.contentIndex(), detection.source(), detection.verified());
}
```

Inputs may carry files (`ScanFile`), such as the images of a message, which are sent in a `multipart/form-data`
request. Every server accepts files, but one that does not scan them, or does not scan them for this client (eg;
without authentication), ignores them and scans the rest of the content. An input with files may have no text, a
server that interprets none of its files returns the neutral risk score of `50` for it, and a detection that relates to
a file names it with `fileIndex()`:

```java
ScannedContent scan = client.scanContent(
    new ContentInput("Your parcel is on hold", ContentType.SMS, null), "15550123456@e164.arpa",
    List.of(ScanFile.of(Path.of("parcel.jpg"))));
```

Here's an example of scanning content and inspecting the classification result:

```java
ScannedContent scan = client.scanContent(
    new ContentInput("Suspicious message to inspect"), "author@example.com", null, null);

ContentClassification classification = scan.getClassification();
System.out.printf("Classification %s with %.1f%% confidence, detected language: %s%n",
    classification.classificationFlag(),
    classification.confidence() * 100.0,
    classification.detectedLanguage());
```

### Searching records

The global search queries all record types with an optional type filter, the search query must contain
at least 2 non-whitespace characters:

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
| `updateOperatorPermissions(uuid, client, management, operator)`             | Grants or revokes permission groups, `null` leaves a group unchanged                 |
| `setOperatorPermissions(uuid, enabled)`                                     | Sets whether the operator has operator-level permissions                             |
| `setClientPermissions(uuid, enabled)`                                       | Sets whether the operator has client-level permissions                               |
| `setManagementPermissions(uuid, enabled)`                                   | Sets whether the operator has management-level permissions                           |
| `updateOperatorConfiguration(uuid, autoAssignReports, automated, isBot)`    | Changes the settings of an operator, `null` leaves a setting unchanged               |
| `setAutoAssign(uuid, enabled)`                                              | Opts the operator in to (or out of) the automatic assignment of every report         |
| `setBot(uuid, isBot)`                                                       | Sets whether the operator is a bot, whose evidence and reports are automated         |
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

An `OperatorRecord` carries its permission groups in `permissions()` and its settings in `configuration()`.
Management permissions inherit client permissions, so revoking client permissions from an operator that keeps
management permissions is rejected with HTTP 409. The configuration is only disclosed to the operator itself and to
operators with operator permissions, anyone else receives `null`; an operator may change its own configuration:

```java
OperatorRecord operator = client.getOperator(uuid);
if (operator.hasManagementPermissions() && operator.isConfigurationVisible()) {
    System.out.println("Bot: " + operator.isBot() + ", auto assigned: " + operator.isAutoAssigned());
}

// Opt in to automated reports only
client.updateOperatorConfiguration(uuid, false, true, null);
```

### Entities

Entities are the subjects tracked by the federation; users, domains, IP addresses, or any other
identifier present in scanned content:

| Method                                                             | Description                                                       |
|--------------------------------------------------------------------|-------------------------------------------------------------------|
| `getEntityRecord(identifier)`                                      | Returns the record of an entity by identifier                     |
| `queryEntity(identifier)`                                          | Returns an entity's relationship group and active blacklist state |
| `searchEntities(query, page, limit, category, by, order)`          | Searches entities by identifier                                   |
| `listEntities(page, limit, category, by, order)`                   | Lists entities                                                    |
| `getTopThreats(limit)`                                             | Lists the highest-risk entities                                   |
| `updateEntity(identifier, metadata)`                               | Updates the metadata of an entity                                 |
| `deleteEntity(identifier)`                                         | Deletes an entity                                                 |
| `setEntityWhitelist(identifier, whitelisted)`                      | (Un)whitelists an entity, exempting it from scans                 |
| `clearEntityReputation(identifier)`                                | Clears the accumulated reputation of an entity                    |
| `setEntityRelationship(identifier, relatedEntityIdentifier, type)` | Sets the relationship between two entities                        |
| `clearEntityRelationship(identifier)`                              | Clears the relationship of an entity                              |
| `pushEntity(host, identifier, metadata)`                           | Pushes an entity to another federation server                     |
| `listEntityAuditLogs(identifier, ...)`                             | Lists the audit log entries involving an entity                   |
| `listEntityBlacklistRecords(identifier, ...)`                      | Lists the blacklist records against an entity                     |
| `listEntityEvidenceRecords(identifier, ...)`                       | Lists the evidence submitted against an entity                    |
| `listEntityReports(identifier, ...)`                               | Lists the reports submitted against an entity                     |

Record listings of entities are `EntityRecord` objects carrying the identifier, SHA-256 identifier (`hash()`),
reputation, relationship hierarchy, and metadata of the entity. Entity identifiers are percent-encoded in request
paths, so addresses such as `user+tag@example.com` can be passed as they are.

A telephone number is the entity `<E.164 number>@e164.arpa`, its local part is the international number without `+`
or separators (`EntityRecord.isTelephone()`). Unless the server publishes `publicTelephoneEntities()`, unauthenticated
requests only see a telephone entity they identify by its address or SHA-256 identifier.

### Evidence

Evidence records are the supporting data attached to entities, either when submitting a report or
manually by operators, and can hold text content, attachments, tags, notes, and confidentiality
flags:

| Method                                                                | Description                                      |
|-----------------------------------------------------------------------|--------------------------------------------------|
| `submitEvidence(entityIdentifier)`                                    | Creates an empty evidence record for an entity   |
| `submitEvidence(identifier, text, note, tag, confidential, metadata)` | Submits evidence with the common fields          |
| `submitEvidence(..., classification, contentType, context)`           | Submits evidence with a content type and context |
| `listEvidence(page, limit, includeConfidential, category, by, order)` | Lists evidence records                           |
| `searchEvidence(query, page, limit, category, by, order)`             | Searches evidence records                        |
| `getEvidenceRecord(uuid)`                                             | Returns a single evidence record                 |
| `getEvidenceAttachments(uuid)`                                        | Lists the attachments of an evidence record      |
| `updateEvidenceConfidentiality(uuid, confidential)`                   | Updates the confidentiality flag of evidence     |
| `updateEvidenceTag(uuid, tag)`                                        | Updates the tag of evidence                      |
| `addEvidenceToReport(evidenceUuid, reportUuid)`                       | Attaches evidence to a report                    |
| `deleteEvidence(uuid)`                                                | Deletes an evidence record                       |

Confidential evidence is hidden from unauthenticated clients by default, listing endpoints
support an `includeConfidential` parameter to include them when the operator has the appropriate
permissions. Evidence the server creates itself or a bot operator submits is `automated()` and created confidential,
the `AUTOMATED` and `NOT_AUTOMATED` categories select it, and evidence about a telephone entity is always
confidential.

### Reports

Reports are the central mechanism of the federation server, submitted against an entity, they group
one or more pieces of evidence and can be classified, assigned to operators, and closed:

| Method                                                            | Description                                       |
|-------------------------------------------------------------------|---------------------------------------------------|
| `submitReport(entity, ContentInput, incidentType, message)`       | Submits one evidence item with a report           |
| `submitReport(entity, List<ContentInput>, incidentType, message)` | Submits one or more evidence items with a report  |
| `listReports(page, limit, category, by, order)`                   | Lists reports                                     |
| `listOpenedReports(page, limit, by, order)`                       | Lists reports that are not yet closed             |
| `searchReports(query, page, limit, category, by, order)`          | Searches reports by content                       |
| `getReport(uuid)`                                                 | Returns a single report                           |
| `listReportEvidenceRecords(reportUuid, ...)`                      | Lists evidence associated with a report           |
| `closeReport(uuid)` / `closeReport(uuid, classification)`         | Closes a report, optionally with a classification |
| `assignOperatorToReport(reportUuid, operatorUuid)`                | Assigns an operator to a report                   |
| `deleteReport(uuid)`                                              | Deletes a report                                  |

`submitReport` returns a `ReportSubmission` object with typed access to the created report
(`getReport()`) and its `List<EvidenceRecord>` (`getEvidence()`). Upload attachments separately
against an evidence record.

### Blacklist records

Blacklist records are the server's mitigation mechanism, an operator can condemn an entity to the
blacklist of the server with an incident type and, optionally, a report backing the decision. The report may
concern another entity, eg; the domain a reported account advertised:

| Method                                                                             | Description                                                            |
|------------------------------------------------------------------------------------|------------------------------------------------------------------------|
| `blacklistEntity(identifier, type)` / `blacklistEntity(identifier, type, expires)` | Blacklists an entity without a supporting report                       |
| `blacklistEntity(identifier, reportUuid, type)`                                    | Blacklists an entity permanently                                       |
| `blacklistEntity(identifier, reportUuid, type, expires)`                           | Blacklists an entity with an expiration timestamp (Unix epoch seconds) |
| `listBlacklistRecords(page, limit, includeLifted, category, by, order, types)`     | Lists blacklist entries, optionally of the given incident types only   |
| `searchBlacklist(query, page, limit, category, by, order, types)`                  | Searches blacklist entries, optionally of the given incident types     |
| `getBlacklistRecord(uuid)`                                                         | Returns a single blacklist record                                      |
| `liftBlacklistRecord(uuid)`                                                        | Lifts the blacklist entry of an entity                                 |
| `extendBlacklistRecord(uuid, seconds)`                                             | Extends the blacklist of an entity by the given seconds                |
| `deleteBlacklistRecord(uuid)`                                                      | Deletes a blacklist record                                             |

### File attachments

Evidence records support file attachments with uploads from disk or URL, and note attachments (plain-text content):

| Method                                                            | Description                                        |
|-------------------------------------------------------------------|----------------------------------------------------|
| `uploadFileAttachment(evidenceUuid, localFilePath)`               | Uploads a local file attachment                    |
| `uploadFileAttachment(evidenceUuid, localFilePath, fileName)`     | Uploads a local file attachment with a custom name |
| `uploadFileAttachmentFromUrl(evidenceUuid, fileUrl)`              | Uploads an attachment from a reachable URL         |
| `uploadFileAttachmentFromUrl(evidenceUuid, fileUrl, maxFileSize)` | Uploads an attachment from a URL with a size limit |
| `uploadFileAttachmentByUrl(evidenceUuid, fileUrl)`                | Server retrieves the attachment from a URL         |
| `uploadNoteAttachment(evidenceUuid, fileName, content)`           | Uploads a plain-text note attachment               |
| `downloadAttachment(attachmentUuid, directoryPath)`               | Downloads an attachment into the given directory   |
| `getAttachmentInfo(uuid)`                                         | Returns the metadata of an attachment              |
| `listAttachments(page, limit, category, by, order)`               | Lists attachments                                  |
| `searchAttachments(query, page, limit, category, by, order)`      | Searches attachments                               |
| `deleteAttachment(uuid)`                                          | Deletes an attachment                              |

File uploads default to a maximum file size of 50 MB, matching the FederationLib client default, the
limit can be raised per-request for URL uploads.

`uploadFileAttachmentFromUrl` downloads the file locally and uploads it, while `uploadFileAttachmentByUrl`
sends only the URL and the server retrieves the file itself. The URL must then be reachable by the server, and
servers reject URLs that resolve to private, loopback or other non-public addresses unless configured otherwise.

### Pagination and sorting

Every listing and search endpoint accepts the same pagination and sorting arguments: `page` (1-based)
and `limit` (minimum 1), followed by an optional category filter, `by` (whatever field to sort by,
case-insensitive, sent uppercase as OFD 2.0 defines), and `order` (`"ASC"` or `"DESC"`). Every combination has a matching overload,
so the calling convention of each method grows gradually, for example:

```java
// Defaults: page 1, limit 100
List<EntityRecord> entities = client.listEntities();

// Explicit pagination
List<EntityRecord> entities = client.listEntities(2, 50);

// Pagination with a category filter, sorted by reputation descending
List<EntityRecord> entities = client.listEntities(2, 50, "WITH_RELATIONSHIP", "REPUTATION", "DESC");
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
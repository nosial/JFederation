package net.nosial.jfederation;

import net.nosial.jfederation.enums.IncidentType;
import net.nosial.jfederation.enums.RecordType;
import net.nosial.jfederation.exceptions.FederationClientException;
import net.nosial.jfederation.records.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import net.nosial.jfederation.records.OperatorCreated;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@Tag("thread-safety")
class ThreadSafetyTest extends FederationClientTestBase {

    private static final int THREAD_COUNT = 8;
    private static final int BULK_COUNT = 20;

    @Test
    void testConcurrentReadOperations() throws Exception {
        String entityUuid = client.pushEntity("concurrent-read.com", "read_test");
        createdEntities.add(entityUuid);

        String evidenceUuid = client.submitEvidence(entityUuid, "Concurrent read test", "test", "concurrent");
        createdEvidenceRecords.add(evidenceUuid);

        int expires = (int) (System.currentTimeMillis() / 1000 + 3600);
        String blacklistUuid = client.blacklistEntity(entityUuid, evidenceUuid, IncidentType.SPAM, expires);
        createdBlacklistRecords.add(blacklistUuid);

        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
        CountDownLatch latch = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < THREAD_COUNT; i++) {
            executor.submit(() -> {
                try {
                    latch.await();
                    EntityRecord entity = client.getEntityRecord(entityUuid);
                    assertNotNull(entity);
                    assertEquals("concurrent-read.com", entity.host());
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    fail("Concurrent read failed: " + e.getMessage());
                }
            });
        }

        latch.countDown();
        executor.shutdown();
        assertTrue(executor.awaitTermination(30, TimeUnit.SECONDS));
        assertEquals(THREAD_COUNT, successCount.get());
    }

    @Test
    void testConcurrentEntityCreation() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
        CountDownLatch latch = new CountDownLatch(1);
        ConcurrentLinkedQueue<String> uuids = new ConcurrentLinkedQueue<>();
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < THREAD_COUNT; i++) {
            int threadNum = i;
            executor.submit(() -> {
                try {
                    latch.await();
                    String host = "thread-entity-" + threadNum + "-" + randomUuid().substring(0, 6) + ".com";
                    String uuid = client.pushEntity(host, "user_" + threadNum);
                    uuids.add(uuid);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    fail("Thread " + Thread.currentThread().getName() + " failed: " + e.getMessage());
                }
            });
        }

        latch.countDown();
        executor.shutdown();
        assertTrue(executor.awaitTermination(30, TimeUnit.SECONDS));
        assertEquals(THREAD_COUNT, successCount.get());

        createdEntities.addAll(uuids);

        for (String uuid : uuids) {
            EntityRecord entity = client.getEntityRecord(uuid);
            assertNotNull(entity);
        }
    }

    @Test
    void testConcurrentEvidenceSubmission() throws Exception {
        String entityUuid = client.pushEntity("concurrent-evidence.com", "evidence_test");
        createdEntities.add(entityUuid);

        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
        CountDownLatch latch = new CountDownLatch(1);
        ConcurrentLinkedQueue<String> evidenceUuids = new ConcurrentLinkedQueue<>();
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < THREAD_COUNT; i++) {
            int threadNum = i;
            executor.submit(() -> {
                try {
                    latch.await();
                    String uuid = client.submitEvidence(entityUuid,
                        "Concurrent evidence " + threadNum, "Thread " + threadNum, "concurrent");
                    evidenceUuids.add(uuid);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    fail("Evidence submission failed: " + e.getMessage());
                }
            });
        }

        latch.countDown();
        executor.shutdown();
        assertTrue(executor.awaitTermination(30, TimeUnit.SECONDS));
        assertEquals(THREAD_COUNT, successCount.get());

        createdEvidenceRecords.addAll(evidenceUuids);

        for (String uuid : evidenceUuids) {
            EvidenceRecord evidence = client.getEvidenceRecord(uuid);
            assertNotNull(evidence);
            assertEquals(entityUuid, evidence.entityUuid());
        }
    }

    @Test
    void testConcurrentPermissionModification() throws Exception {
        OperatorCreated targetUuidCreated = client.createOperator("permission_target");
        String targetUuid = targetUuidCreated.uuid();
        createdOperators.add(targetUuid);

        FederationClient managerA = createLimitedOperator("mgr_a", true, true, true);
        FederationClient managerB = createLimitedOperator("mgr_b", true, true, true);
        FederationClient managerC = createLimitedOperator("mgr_c", true, true, true);

        ExecutorService executor = Executors.newFixedThreadPool(3);
        CountDownLatch latch = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger(0);

        executor.submit(() -> {
            try {
                latch.await();
                managerA.setManagementPermissions(targetUuid, false);
                managerA.setOperatorPermissions(targetUuid, true);
                successCount.incrementAndGet();
            } catch (Exception e) {
                fail("Manager A failed: " + e.getMessage());
            }
        });

        executor.submit(() -> {
            try {
                latch.await();
                managerB.setClientPermissions(targetUuid, true);
                managerB.setOperatorPermissions(targetUuid, false);
                successCount.incrementAndGet();
            } catch (Exception e) {
                fail("Manager B failed: " + e.getMessage());
            }
        });

        executor.submit(() -> {
            try {
                latch.await();
                managerC.setManagementPermissions(targetUuid, true);
                managerC.setClientPermissions(targetUuid, false);
                successCount.incrementAndGet();
            } catch (Exception e) {
                fail("Manager C failed: " + e.getMessage());
            }
        });

        latch.countDown();
        executor.shutdown();
        assertTrue(executor.awaitTermination(30, TimeUnit.SECONDS));
        assertEquals(3, successCount.get());

        OperatorRecord target = client.getOperator(targetUuid);
        assertNotNull(target);
    }

    @Test
    void testConcurrentDeleteOperations() throws Exception {
        String entityUuid = client.pushEntity("concurrent-delete.com", "delete_test");
        createdEntities.add(entityUuid);

        String evidenceUuid = client.submitEvidence(entityUuid, "Delete test evidence", "test", "delete");
        createdEvidenceRecords.add(evidenceUuid);

        int expires = (int) (System.currentTimeMillis() / 1000 + 3600);
        String blacklistUuid = client.blacklistEntity(entityUuid, evidenceUuid, IncidentType.SPAM, expires);
        createdBlacklistRecords.add(blacklistUuid);

        FederationClient altClient = new FederationClient(serverEndpoint, serverAccessToken);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch latch = new CountDownLatch(1);
        AtomicInteger deleteSuccess = new AtomicInteger(0);

        executor.submit(() -> {
            try {
                latch.await();
                client.deleteBlacklistRecord(blacklistUuid);
                deleteSuccess.incrementAndGet();
            } catch (Exception ignored) {
            }
        });

        executor.submit(() -> {
            try {
                latch.await();
                altClient.deleteBlacklistRecord(blacklistUuid);
                deleteSuccess.incrementAndGet();
            } catch (Exception ignored) {
            }
        });

        latch.countDown();
        executor.shutdown();
        assertTrue(executor.awaitTermination(15, TimeUnit.SECONDS));

        removeFromCleanup(createdBlacklistRecords, blacklistUuid);

        try {
            client.getBlacklistRecord(blacklistUuid);
            fail("Expected record to be deleted");
        } catch (FederationClientException e) {
            assertTrue(e.getStatusCode() == 404 || e.getStatusCode() == 410);
        }

        altClient.close();
    }

    @Test
    void testSingleClientMultiThreadAccess() throws Exception {
        String baseHost = "multi-thread-client-" + randomUuid().substring(0, 6) + ".com";
        String entityUuid = client.pushEntity(baseHost, "shared_client_test");
        createdEntities.add(entityUuid);

        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
        CountDownLatch latch = new CountDownLatch(1);
        AtomicInteger readSuccess = new AtomicInteger(0);

        for (int i = 0; i < THREAD_COUNT; i++) {
            executor.submit(() -> {
                try {
                    latch.await();
                    for (int j = 0; j < 5; j++) {
                        EntityRecord entity = client.getEntityRecord(entityUuid);
                        assertNotNull(entity);
                        Thread.sleep(1);
                    }
                    readSuccess.incrementAndGet();
                } catch (Exception e) {
                    fail("Multi-thread read failed: " + e.getMessage());
                }
            });
        }

        latch.countDown();
        executor.shutdown();
        assertTrue(executor.awaitTermination(30, TimeUnit.SECONDS));
        assertEquals(THREAD_COUNT, readSuccess.get());
    }

    @Test
    void testRaceConditionCreateAndDelete() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(6);
        CountDownLatch latch = new CountDownLatch(1);
        AtomicInteger createCount = new AtomicInteger(0);
        AtomicInteger deleteAttempts = new AtomicInteger(0);

        for (int i = 0; i < 3; i++) {
            executor.submit(() -> {
                try {
                    latch.await();
                    String host = "race-create-" + randomUuid().substring(0, 6) + ".com";
                    String uuid = client.pushEntity(host, "race_user");
                    synchronized (createdEntities) {
                        createdEntities.add(uuid);
                    }
                    createCount.incrementAndGet();
                } catch (Exception e) {
                    fail("Create failed: " + e.getMessage());
                }
            });
        }

        for (int i = 0; i < 3; i++) {
            executor.submit(() -> {
                try {
                    latch.await();
                    String host = "race-delete-candidate-" + randomUuid().substring(0, 6) + ".com";
                    String uuid = client.pushEntity(host, "race_delete_user");
                    synchronized (createdEntities) {
                        createdEntities.add(uuid);
                    }
                    createCount.incrementAndGet();

                    client.deleteEntity(uuid);
                    synchronized (createdEntities) {
                        createdEntities.remove(uuid);
                        removeFromCleanup(createdEntities, uuid);
                    }
                    deleteAttempts.incrementAndGet();

                    try {
                        client.getEntityRecord(uuid);
                        fail("Expected 404 after delete");
                    } catch (FederationClientException e) {
                        assertTrue(e.getStatusCode() == 404 || e.getStatusCode() == 410);
                    }
                } catch (Exception e) {
                    fail("Race delete failed: " + e.getMessage());
                }
            });
        }

        latch.countDown();
        executor.shutdown();
        assertTrue(executor.awaitTermination(30, TimeUnit.SECONDS));
        assertEquals(6, createCount.get() + deleteAttempts.get() - 3);
    }

    @Test
    void testHighVolumeBulkOperations() throws Exception {
        ConcurrentLinkedQueue<String> entityUuids = new ConcurrentLinkedQueue<>();

        for (int i = 0; i < BULK_COUNT; i++) {
            String host = "bulk-entity-" + i + "-" + randomUuid().substring(0, 6) + ".com";
            String uuid = client.pushEntity(host, "bulk_user_" + i);
            entityUuids.add(uuid);
        }

        createdEntities.addAll(entityUuids);

        List<EntityRecord> page1 = client.listEntities(1, 10);
        List<EntityRecord> page2 = client.listEntities(2, 10);
        assertFalse(page1.isEmpty());
        assertFalse(page2.isEmpty());

        Set<String> allReturned = new HashSet<>();
        for (EntityRecord e : page1) allReturned.add(e.uuid());
        for (EntityRecord e : page2) allReturned.add(e.uuid());

        int found = 0;
        for (String uuid : entityUuids) {
            if (allReturned.contains(uuid)) found++;
        }
        assertTrue(found >= BULK_COUNT * 0.5, "At least half of bulk entities should appear in listings");
    }

    @Test
    void testBulkCrossRecordLifecycle() throws Exception {
        ConcurrentLinkedQueue<String> entityUuids = new ConcurrentLinkedQueue<>();
        ConcurrentLinkedQueue<String> evidenceUuids = new ConcurrentLinkedQueue<>();
        ConcurrentLinkedQueue<String> blacklistUuids = new ConcurrentLinkedQueue<>();

        for (int i = 0; i < 10; i++) {
            String host = "cross-lifecycle-" + i + "-" + randomUuid().substring(0, 6) + ".com";
            String entityUuid = client.pushEntity(host, "cross_user_" + i);
            entityUuids.add(entityUuid);

            String evidenceUuid = client.submitEvidence(entityUuid,
                "Cross lifecycle evidence " + i, "Bulk test", "cross");
            evidenceUuids.add(evidenceUuid);

            int expires = (int) (System.currentTimeMillis() / 1000 + 7200);
            String blacklistUuid = client.blacklistEntity(entityUuid, evidenceUuid,
                i % 2 == 0 ? IncidentType.SPAM : IncidentType.SCAM, expires);
            blacklistUuids.add(blacklistUuid);
        }

        createdEntities.addAll(entityUuids);
        createdEvidenceRecords.addAll(evidenceUuids);
        createdBlacklistRecords.addAll(blacklistUuids);

        for (int i = 0; i < 5; i++) {
            String operatorUuid = client.createOperator("cross_bulk_operator_" + i + "_" + randomUuid().substring(0, 4)).uuid();
            createdOperators.add(operatorUuid);

            client.setManagementPermissions(operatorUuid, i % 2 == 0);
            client.setOperatorPermissions(operatorUuid, i % 3 == 0);
            client.setClientPermissions(operatorUuid, i % 2 == 1);
        }

        for (String uuid : entityUuids) {
            EntityRecord entity = client.getEntityRecord(uuid);
            assertNotNull(entity);
        }

        for (String uuid : new ArrayList<>(evidenceUuids)) {
            EvidenceRecord evidence = client.getEvidenceRecord(uuid);
            assertNotNull(evidence);
        }
    }

    @Test
    void testMassSearchUnderLoad() throws Exception {
        String sharedKeyword = "masssearch-" + randomUuid().substring(0, 6);
        ConcurrentLinkedQueue<String> entityUuids = new ConcurrentLinkedQueue<>();

        for (int i = 0; i < 15; i++) {
            String host = i + "-" + sharedKeyword + ".com";
            String uuid = client.pushEntity(host, sharedKeyword + "_user_" + i);
            entityUuids.add(uuid);
        }

        createdEntities.addAll(entityUuids);

        for (int i = 0; i < 10; i++) {
            String evidenceUuid = client.submitEvidence(
                entityUuids.stream().findFirst().get(),
                "Evidence for " + sharedKeyword + " " + i,
                "mass search test", "masssearch");
            createdEvidenceRecords.add(evidenceUuid);
        }

        var searchResults = client.search(sharedKeyword, null, 1, 50);
        assertNotNull(searchResults);
        assertTrue(searchResults.size() >= 10, "Search should return at least 10 results with shared keyword");

        var page1 = client.search(sharedKeyword, null, 1, 5);
        var page2 = client.search(sharedKeyword, null, 2, 5);
        assertNotNull(page1);
        assertNotNull(page2);

        Set<String> page1Uuids = new HashSet<>();
        for (var r : page1) {
            if (r.type() == RecordType.ENTITY) {
                page1Uuids.add(r.<EntityRecord>getRecord().uuid());
            }
        }
        for (var r : page2) {
            if (r.type() == RecordType.ENTITY) {
                String uuid = r.<EntityRecord>getRecord().uuid();
                assertFalse(page1Uuids.contains(uuid),
                    "Search pagination should not return duplicates across pages");
            }
        }
    }

    @Test
    void testFullLifecycleWorkflow() throws Exception {
        String entityHost = "full-lifecycle-" + randomUuid().substring(0, 6) + ".com";
        String entityUuid = client.pushEntity(entityHost, "lifecycle_user");
        createdEntities.add(entityUuid);

        EntityRecord createdEntity = client.getEntityRecord(entityUuid);
        assertEquals(entityHost, createdEntity.host());

        String evidenceUuid = client.submitEvidence(entityUuid, "Lifecycle evidence", "initial", "lifecycle");
        createdEvidenceRecords.add(evidenceUuid);
        assertNotNull(evidenceUuid);

        EvidenceRecord createdEvidence = client.getEvidenceRecord(evidenceUuid);
        assertEquals("Lifecycle evidence", createdEvidence.textContent());

        client.updateEvidenceConfidentiality(evidenceUuid, true);
        EvidenceRecord confidentialEvidence = client.getEvidenceRecord(evidenceUuid);
        assertTrue(confidentialEvidence.confidential());

        client.updateEvidenceConfidentiality(evidenceUuid, false);
        EvidenceRecord publicEvidence = client.getEvidenceRecord(evidenceUuid);
        assertFalse(publicEvidence.confidential());

        int expires = (int) (System.currentTimeMillis() / 1000 + 3600);
        String blacklistUuid = client.blacklistEntity(entityUuid, evidenceUuid, IncidentType.SPAM, expires);
        createdBlacklistRecords.add(blacklistUuid);

        BlacklistRecord blacklistRecord = client.getBlacklistRecord(blacklistUuid);
        assertEquals(entityUuid, blacklistRecord.entityUuid());

        client.liftBlacklistRecord(blacklistUuid);
        BlacklistRecord liftedRecord = client.getBlacklistRecord(blacklistUuid);
        assertTrue(liftedRecord.lifted());

        String reportEntity = "report-lifecycle-" + randomUuid().substring(0, 6) + ".com";
        String reportEntityUuid = client.pushEntity(reportEntity, "report_user");
        createdEntities.add(reportEntityUuid);

        ReportSubmission submission = client.submitReport(reportEntityUuid, "Lifecycle report", IncidentType.SCAM);
        createdReports.add(submission.getReport().uuid());
        createdEvidenceRecords.add(submission.getEvidence().get(0).uuid());

        ReportRecord report = client.getReport(submission.getReport().uuid());
        assertNotNull(report);

        client.deleteReport(submission.getReport().uuid());
        expectRequestFailure(() -> client.getReport(submission.getReport().uuid()), 404);
    }

    @Test
    void testCrossClientPermissionIsolation() throws Exception {
        FederationClient noPermClient = createLimitedOperator("no_perm");
        FederationClient clientPermOnly = createLimitedOperator("client_only", true);
        FederationClient fullManager = createLimitedOperator("full_mgr", true, true, true);

        String opName = "target_op_" + randomUuid().substring(0, 5);
        OperatorCreated targetOpUuidCreated = client.createOperator(opName);
        String targetOpUuid = targetOpUuidCreated.uuid();
        createdOperators.add(targetOpUuid);

        expectRequestFailure(
            () -> { noPermClient.createOperator("should_fail"); },
            new int[]{403, 401});

        expectRequestFailure(
            () -> noPermClient.pushEntity("no-perm.com", "test"),
            new int[]{403, 401});

        String entityByClient = clientPermOnly.pushEntity("client-perm-" + randomUuid().substring(0, 6) + ".com", "test");
        createdEntities.add(entityByClient);
        assertNotNull(entityByClient);

        String entityByManager = fullManager.pushEntity("manager-perm-" + randomUuid().substring(0, 6) + ".com", "test");
        createdEntities.add(entityByManager);
        assertNotNull(entityByManager);

        expectRequestFailure(
            () -> clientPermOnly.setManagementPermissions(targetOpUuid, true),
            new int[]{403, 401});

        fullManager.setManagementPermissions(targetOpUuid, true);
        fullManager.setOperatorPermissions(targetOpUuid, true);
        fullManager.setClientPermissions(targetOpUuid, true);

        OperatorRecord target = client.getOperator(targetOpUuid);
        assertNotNull(target);
    }

    @Test
    void testConcurrentReadWriteMix() throws Exception {
        String entityUuid = client.pushEntity("read-write-mix-" + randomUuid().substring(0, 6) + ".com", "mix_test");
        createdEntities.add(entityUuid);

        ConcurrentLinkedQueue<String> evidenceUuids = new ConcurrentLinkedQueue<>();
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
        CountDownLatch latch = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < 4; i++) {
            int threadNum = i;
            executor.submit(() -> {
                try {
                    latch.await();
                    String uuid = client.submitEvidence(entityUuid,
                        "Writer evidence " + threadNum, "Writer " + threadNum, "readwrite");
                    evidenceUuids.add(uuid);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    fail("Writer " + Thread.currentThread().getName() + " failed: " + e.getMessage());
                }
            });
        }

        for (int i = 0; i < 4; i++) {
            executor.submit(() -> {
                try {
                    latch.await();
                    EntityRecord entity = client.getEntityRecord(entityUuid);
                    assertNotNull(entity);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    fail("Reader failed: " + e.getMessage());
                }
            });
        }

        latch.countDown();
        executor.shutdown();
        assertTrue(executor.awaitTermination(30, TimeUnit.SECONDS));
        assertEquals(THREAD_COUNT, successCount.get());

        createdEvidenceRecords.addAll(evidenceUuids);
    }

    @Test
    void testConcurrentConfidentialityToggle() throws Exception {
        String entityUuid = client.pushEntity("confidential-toggle-" + randomUuid().substring(0, 6) + ".com", "conf_test");
        createdEntities.add(entityUuid);

        List<String> evidenceUuids = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            evidenceUuids.add(client.submitEvidence(entityUuid, "Confidential toggle test " + i, "test", "conf_toggle"));
        }
        createdEvidenceRecords.addAll(evidenceUuids);

        ExecutorService executor = Executors.newFixedThreadPool(4);
        CountDownLatch latch = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger(0);
        ConcurrentLinkedQueue<Throwable> errors = new ConcurrentLinkedQueue<>();

        for (int i = 0; i < 4; i++) {
            String evidenceUuid = evidenceUuids.get(i);
            executor.submit(() -> {
                try {
                    latch.await();
                    client.updateEvidenceConfidentiality(evidenceUuid, true);
                    EvidenceRecord rec1 = client.getEvidenceRecord(evidenceUuid);

                    client.updateEvidenceConfidentiality(evidenceUuid, false);
                    EvidenceRecord rec2 = client.getEvidenceRecord(evidenceUuid);

                    assertNotNull(rec1);
                    assertNotNull(rec2);
                    successCount.incrementAndGet();
                } catch (Throwable t) {
                    errors.add(t);
                }
            });
        }

        latch.countDown();
        executor.shutdown();
        assertTrue(executor.awaitTermination(30, TimeUnit.SECONDS));
        assertTrue(errors.isEmpty(), "Confidentiality toggle failed: " + errors);
        assertEquals(4, successCount.get());
    }

    @Test
    void testMultipleAnonymousClients() throws Exception {
        String entityUuid = client.pushEntity("anon-multi-" + randomUuid().substring(0, 6) + ".com", "anon_test");
        createdEntities.add(entityUuid);

        String evidenceUuid = client.submitEvidence(entityUuid, "Anonymous multi test", "test", "anon");
        createdEvidenceRecords.add(evidenceUuid);

        client.updateEvidenceConfidentiality(evidenceUuid, true);

        ExecutorService executor = Executors.newFixedThreadPool(4);
        CountDownLatch latch = new CountDownLatch(1);
        AtomicInteger deniedCount = new AtomicInteger(0);
        AtomicInteger allowedCount = new AtomicInteger(0);

        for (int i = 0; i < 4; i++) {
            executor.submit(() -> {
                try {
                    latch.await();
                    FederationClient anonClient = createAnonymousClient();
                    try {
                        anonClient.getEvidenceRecord(evidenceUuid);
                        allowedCount.incrementAndGet();
                    } catch (FederationClientException e) {
                        if (e.getStatusCode() == 403 || e.getStatusCode() == 401) {
                            deniedCount.incrementAndGet();
                        }
                    } finally {
                        anonClient.close();
                    }
                } catch (Exception e) {
                    fail("Anonymous client test failed: " + e.getMessage());
                }
            });
        }

        latch.countDown();
        executor.shutdown();
        assertTrue(executor.awaitTermination(30, TimeUnit.SECONDS));
        assertEquals(4, deniedCount.get() + allowedCount.get());
        assertEquals(4, deniedCount.get(),
            "All anonymous access to confidential evidence should be denied");
    }

    @Test
    void testConcurrentIndependentWorkflows() throws Exception {
        int workflowCount = 5;
        ExecutorService executor = Executors.newFixedThreadPool(workflowCount);
        CountDownLatch latch = new CountDownLatch(1);
        ConcurrentLinkedQueue<String> allEntities = new ConcurrentLinkedQueue<>();
        ConcurrentLinkedQueue<String> allEvidence = new ConcurrentLinkedQueue<>();
        ConcurrentLinkedQueue<String> allBlacklists = new ConcurrentLinkedQueue<>();
        AtomicInteger successCount = new AtomicInteger(0);

        for (int wf = 0; wf < workflowCount; wf++) {
            int workflowId = wf;
            executor.submit(() -> {
                try {
                    latch.await();
                    String host = "workflow-" + workflowId + "-" + randomUuid().substring(0, 6) + ".com";
                    String eUuid = client.pushEntity(host, "wf_user_" + workflowId);
                    allEntities.add(eUuid);

                    String evUuid = client.submitEvidence(eUuid,
                        "Workflow " + workflowId + " evidence", "wf " + workflowId, "workflow_" + workflowId);
                    allEvidence.add(evUuid);

                    int expires = (int) (System.currentTimeMillis() / 1000 + 3600);
                    String bUuid = client.blacklistEntity(eUuid, evUuid, IncidentType.SCAM, expires);
                    allBlacklists.add(bUuid);

                    successCount.incrementAndGet();
                } catch (Exception e) {
                    fail("Workflow " + workflowId + " failed: " + e.getMessage());
                }
            });
        }

        latch.countDown();
        executor.shutdown();
        assertTrue(executor.awaitTermination(30, TimeUnit.SECONDS));
        assertEquals(workflowCount, successCount.get());

        createdEntities.addAll(allEntities);
        createdEvidenceRecords.addAll(allEvidence);
        createdBlacklistRecords.addAll(allBlacklists);

        for (String uuid : allEntities) {
            EntityRecord entity = client.getEntityRecord(uuid);
            assertNotNull(entity);
        }
        for (String uuid : allEvidence) {
            EvidenceRecord evidence = client.getEvidenceRecord(uuid);
            assertNotNull(evidence);
        }
    }

    @Test
    void testConcurrentOperatorCreationUnderLoad() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(10);

        class OpResult {
            final String uuid;
            final String name;
            OpResult(String uuid, String name) { this.uuid = uuid; this.name = name; }
        }

        ConcurrentLinkedQueue<OpResult> operatorResults = new ConcurrentLinkedQueue<>();
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < 10; i++) {
            int threadNum = i;
            executor.submit(() -> {
                try {
                    String name = "bulk_op_" + threadNum + "_" + randomUuid().substring(0, 4);
                    OperatorCreated uuidCreated = client.createOperator(name);
        String uuid = uuidCreated.uuid();
                    operatorResults.add(new OpResult(uuid, name));
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    fail("Operator creation failed: " + e.getMessage());
                }
            });
        }

        executor.shutdown();
        assertTrue(executor.awaitTermination(30, TimeUnit.SECONDS));
        assertEquals(10, successCount.get());

        for (OpResult result : operatorResults) {
            createdOperators.add(result.uuid);
            OperatorRecord op = client.getOperator(result.uuid);
            assertNotNull(op);
        }
    }

    @Test
    void testSequentialBulkLifecycleAllTypes() throws Exception {
        Set<String> entitySet = new HashSet<>();
        Set<String> evidenceSet = new HashSet<>();
        Set<String> blacklistSet = new HashSet<>();
        Set<String> operatorSet = new HashSet<>();

        for (int i = 0; i < 5; i++) {
            String entityHost = "seq-bulk-" + i + "-" + randomUuid().substring(0, 6) + ".com";
            String entityUuid = client.pushEntity(entityHost, "seq_user_" + i);
            entitySet.add(entityUuid);

            String opName = "seq_op_" + i + "_" + randomUuid().substring(0, 4);
            OperatorCreated opUuidCreated = client.createOperator(opName);
        String opUuid = opUuidCreated.uuid();
            operatorSet.add(opUuid);

            client.setManagementPermissions(opUuid, i == 0);
        }

        String firstEntity = entitySet.iterator().next();
        for (int i = 0; i < 5; i++) {
            String evUuid = client.submitEvidence(firstEntity,
                "Seq bulk evidence " + i, "seq note " + i, "seq_bulk");
            evidenceSet.add(evUuid);

            int expires = (int) (System.currentTimeMillis() / 1000 + 3600 + (i * 1000));
            String bUuid = client.blacklistEntity(firstEntity, evUuid, IncidentType.SPAM, expires);
            blacklistSet.add(bUuid);
        }

        createdEntities.addAll(entitySet);
        createdEvidenceRecords.addAll(evidenceSet);
        createdBlacklistRecords.addAll(blacklistSet);
        createdOperators.addAll(operatorSet);

        List<EntityRecord> entities = client.listEntities(1, 100);
        Map<String, EntityRecord> entityMap = new HashMap<>();
        for (EntityRecord e : entities) entityMap.put(e.uuid(), e);
        for (String uuid : entitySet) {
            assertTrue(entityMap.containsKey(uuid), "Bulk entity " + uuid + " should appear in listing");
        }

        List<EvidenceRecord> allEvidence = client.listEvidence(1, 100, true);
        Set<String> returnedEvidenceUuids = new HashSet<>();
        for (EvidenceRecord e : allEvidence) returnedEvidenceUuids.add(e.uuid());
        for (String uuid : evidenceSet) {
            assertTrue(returnedEvidenceUuids.contains(uuid), "Bulk evidence " + uuid + " should appear in listing");
        }
    }

    @Test
    void testConcurrentCreateAndList() throws Exception {
        String baseHost = "concurrent-cl-" + randomUuid().substring(0, 6);
        ConcurrentLinkedQueue<String> createdUuids = new ConcurrentLinkedQueue<>();
        AtomicInteger createSuccess = new AtomicInteger(0);
        AtomicInteger listSuccess = new AtomicInteger(0);

        ExecutorService executor = Executors.newFixedThreadPool(8);
        CountDownLatch latch = new CountDownLatch(1);

        for (int i = 0; i < 5; i++) {
            int threadNum = i;
            executor.submit(() -> {
                try {
                    latch.await();
                    String uuid = client.pushEntity(baseHost + "-" + threadNum + "-" + randomUuid().substring(0, 4) + ".com", "user_" + threadNum);
                    createdUuids.add(uuid);
                    createSuccess.incrementAndGet();
                } catch (Exception e) {
                    fail("Create failed: " + e.getMessage());
                }
            });
        }

        for (int i = 0; i < 3; i++) {
            executor.submit(() -> {
                try {
                    latch.await();
                    List<EntityRecord> results = client.listEntities(1, 50);
                    assertNotNull(results);
                    listSuccess.incrementAndGet();
                } catch (Exception e) {
                    fail("List failed: " + e.getMessage());
                }
            });
        }

        latch.countDown();
        executor.shutdown();
        assertTrue(executor.awaitTermination(30, TimeUnit.SECONDS));
        assertEquals(5, createSuccess.get());
        assertEquals(3, listSuccess.get());

        createdEntities.addAll(createdUuids);
    }
}

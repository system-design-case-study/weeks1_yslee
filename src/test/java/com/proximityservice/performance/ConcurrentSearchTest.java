package com.proximityservice.performance;

import static org.assertj.core.api.Assertions.assertThat;

import com.proximityservice.domain.Business;
import com.proximityservice.dto.BusinessCreateRequest;
import com.proximityservice.dto.NearbySearchResponse;
import com.proximityservice.support.BaseIntegrationTest;
import com.proximityservice.support.TestDataFactory;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("performance")
@DisplayName("동시 검색 정확성 테스트")
class ConcurrentSearchTest extends BaseIntegrationTest {

    @Test
    @DisplayName("10 스레드 x 50 검색: 예외 없음, 결과 정확")
    void concurrentReadsShouldBeAccurate() throws Exception {
        // 100건 시딩
        List<BusinessCreateRequest> requests = TestDataFactory.generateBulk(
                TestDataFactory.GANGNAM_LAT, TestDataFactory.GANGNAM_LNG, 100, 5000);
        for (BusinessCreateRequest req : requests) {
            businessService.create(req);
        }

        int threads = 10;
        int searchesPerThread = 50;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(threads);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);
        CopyOnWriteArrayList<Integer> resultCounts = new CopyOnWriteArrayList<>();

        for (int t = 0; t < threads; t++) {
            executor.submit(() -> {
                try {
                    for (int i = 0; i < searchesPerThread; i++) {
                        NearbySearchResponse response = nearbySearchService.searchNearby(
                                TestDataFactory.GANGNAM_LAT, TestDataFactory.GANGNAM_LNG, 5000);
                        resultCounts.add(response.total());
                        successCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    errorCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        printHeader("동시 검색: 10 스레드 x 50 검색");
        printResult("총 검색 수", threads * searchesPerThread);
        printResult("성공", successCount.get());
        printResult("에러", errorCount.get());
        printResult("결과 수 (min)", resultCounts.stream().mapToInt(i -> i).min().orElse(0));
        printResult("결과 수 (max)", resultCounts.stream().mapToInt(i -> i).max().orElse(0));
        printPassFail("에러 없음", errorCount.get() == 0);

        assertThat(errorCount.get()).isEqualTo(0);
        assertThat(successCount.get()).isEqualTo(threads * searchesPerThread);
        // 모든 검색 결과가 동일해야 함
        assertThat(resultCounts.stream().distinct().count()).isEqualTo(1);
    }

    @Test
    @DisplayName("5 읽기 + 2 쓰기 동시: 데이터 무결성 유지")
    void concurrentReadsAndWritesShouldMaintainIntegrity() throws Exception {
        // 초기 50건 시딩
        List<BusinessCreateRequest> requests = TestDataFactory.generateBulk(
                TestDataFactory.GANGNAM_LAT, TestDataFactory.GANGNAM_LNG, 50, 3000);
        for (BusinessCreateRequest req : requests) {
            businessService.create(req);
        }

        int readerThreads = 5;
        int writerThreads = 2;
        int readsPerThread = 30;
        int writesPerThread = 10;

        ExecutorService executor = Executors.newFixedThreadPool(readerThreads + writerThreads);
        CountDownLatch latch = new CountDownLatch(readerThreads + writerThreads);
        AtomicInteger readSuccess = new AtomicInteger(0);
        AtomicInteger readError = new AtomicInteger(0);
        AtomicInteger writeSuccess = new AtomicInteger(0);
        AtomicInteger writeError = new AtomicInteger(0);

        // 읽기 스레드
        for (int t = 0; t < readerThreads; t++) {
            executor.submit(() -> {
                try {
                    for (int i = 0; i < readsPerThread; i++) {
                        nearbySearchService.searchNearby(
                                TestDataFactory.GANGNAM_LAT, TestDataFactory.GANGNAM_LNG, 5000);
                        readSuccess.incrementAndGet();
                    }
                } catch (Exception e) {
                    readError.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        // 쓰기 스레드
        for (int t = 0; t < writerThreads; t++) {
            final int threadIdx = t;
            executor.submit(() -> {
                try {
                    for (int i = 0; i < writesPerThread; i++) {
                        businessService.create(TestDataFactory.businessAt(
                                "동시쓰기_" + threadIdx + "_" + i,
                                TestDataFactory.GANGNAM_LAT + (i * 0.0001),
                                TestDataFactory.GANGNAM_LNG,
                                "cafe"));
                        writeSuccess.incrementAndGet();
                    }
                } catch (Exception e) {
                    writeError.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        long mysqlCount = businessRepository.count();
        long redisCount = getRedisMemberCount();

        printHeader("동시 읽기/쓰기: 5R + 2W 스레드");
        printResult("읽기 성공", readSuccess.get());
        printResult("읽기 에러", readError.get());
        printResult("쓰기 성공", writeSuccess.get());
        printResult("쓰기 에러", writeError.get());
        printResult("MySQL 최종", mysqlCount + "건");
        printResult("Redis 최종", redisCount + "건");
        printPassFail("에러 없음", readError.get() == 0 && writeError.get() == 0);
        printPassFail("MySQL == Redis", mysqlCount == redisCount);

        assertThat(readError.get()).isEqualTo(0);
        assertThat(writeError.get()).isEqualTo(0);
        assertThat(mysqlCount).isEqualTo(redisCount);
    }
}

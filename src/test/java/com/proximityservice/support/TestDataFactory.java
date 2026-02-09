package com.proximityservice.support;

import com.proximityservice.dto.BusinessCreateRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public final class TestDataFactory {

    private TestDataFactory() {}

    // ── 서울 5대 랜드마크 좌표 ──
    public static final double GANGNAM_LAT = 37.4979;
    public static final double GANGNAM_LNG = 127.0276;

    public static final double HONGDAE_LAT = 37.5563;
    public static final double HONGDAE_LNG = 126.9236;

    public static final double JAMSIL_LAT = 37.5133;
    public static final double JAMSIL_LNG = 127.1001;

    public static final double SEOUL_STATION_LAT = 37.5547;
    public static final double SEOUL_STATION_LNG = 126.9707;

    public static final double MYEONGDONG_LAT = 37.5636;
    public static final double MYEONGDONG_LNG = 126.9869;

    // 서울 중심 (남산타워 근처)
    public static final double SEOUL_CENTER_LAT = 37.5512;
    public static final double SEOUL_CENTER_LNG = 126.9882;

    private static final String[] CATEGORIES = {
            "korean_food", "cafe", "bar", "convenience", "pharmacy"
    };

    /**
     * Haversine 공식으로 두 좌표 간 거리(미터) 계산.
     * Redis GEODIST 결과를 독립적으로 검증하기 위한 레퍼런스 구현.
     */
    public static double haversineMeters(double lat1, double lng1, double lat2, double lng2) {
        double R = 6_371_000.0; // 지구 반지름 (미터)
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }

    /**
     * 중심 좌표 주위에 결정적(seed=42) 클러스터 데이터 생성.
     * @param radiusMeters 클러스터의 최대 반경(미터)
     */
    public static List<BusinessCreateRequest> generateCluster(
            double centerLat, double centerLng, int count, String category, double radiusMeters) {
        Random random = new Random(42);
        List<BusinessCreateRequest> requests = new ArrayList<>();

        for (int i = 0; i < count; i++) {
            double angle = random.nextDouble() * 2 * Math.PI;
            double dist = random.nextDouble() * radiusMeters;

            double dLat = (dist * Math.cos(angle)) / 111_320.0;
            double dLng = (dist * Math.sin(angle)) / (111_320.0 * Math.cos(Math.toRadians(centerLat)));

            requests.add(new BusinessCreateRequest(
                    category + "_" + (i + 1),
                    "서울시 테스트 주소 " + (i + 1),
                    centerLat + dLat,
                    centerLng + dLng,
                    category,
                    null,
                    null
            ));
        }
        return requests;
    }

    /**
     * 대량 시딩 데이터 생성 (카테고리 순환, seed=42).
     */
    public static List<BusinessCreateRequest> generateBulk(
            double centerLat, double centerLng, int count, double radiusMeters) {
        Random random = new Random(42);
        List<BusinessCreateRequest> requests = new ArrayList<>();

        for (int i = 0; i < count; i++) {
            double angle = random.nextDouble() * 2 * Math.PI;
            double dist = random.nextDouble() * radiusMeters;

            double dLat = (dist * Math.cos(angle)) / 111_320.0;
            double dLng = (dist * Math.sin(angle)) / (111_320.0 * Math.cos(Math.toRadians(centerLat)));

            String cat = CATEGORIES[i % CATEGORIES.length];
            requests.add(new BusinessCreateRequest(
                    "업체_" + (i + 1),
                    "서울시 테스트 주소 " + (i + 1),
                    centerLat + dLat,
                    centerLng + dLng,
                    cat,
                    null,
                    null
            ));
        }
        return requests;
    }

    // ── 랜드마크별 팩토리 메서드 ──

    public static BusinessCreateRequest gangnamBusiness(String name) {
        return new BusinessCreateRequest(name, "서울시 강남구", GANGNAM_LAT, GANGNAM_LNG, "korean_food", null, null);
    }

    public static BusinessCreateRequest hongdaeBusiness(String name) {
        return new BusinessCreateRequest(name, "서울시 마포구 홍대", HONGDAE_LAT, HONGDAE_LNG, "cafe", null, null);
    }

    public static BusinessCreateRequest jamsilBusiness(String name) {
        return new BusinessCreateRequest(name, "서울시 송파구 잠실", JAMSIL_LAT, JAMSIL_LNG, "bar", null, null);
    }

    public static BusinessCreateRequest seoulStationBusiness(String name) {
        return new BusinessCreateRequest(name, "서울시 용산구 서울역", SEOUL_STATION_LAT, SEOUL_STATION_LNG, "convenience", null, null);
    }

    public static BusinessCreateRequest myeongdongBusiness(String name) {
        return new BusinessCreateRequest(name, "서울시 중구 명동", MYEONGDONG_LAT, MYEONGDONG_LNG, "pharmacy", null, null);
    }

    /**
     * 특정 좌표로 BusinessCreateRequest 생성.
     */
    public static BusinessCreateRequest businessAt(String name, double lat, double lng, String category) {
        return new BusinessCreateRequest(name, "서울시 테스트 주소", lat, lng, category, null, null);
    }

    /**
     * 중심 좌표에서 특정 거리(미터)만큼 북쪽으로 이동한 좌표의 위도 반환.
     */
    public static double latOffsetMeters(double baseLat, double meters) {
        return baseLat + (meters / 111_320.0);
    }

    /**
     * 중심 좌표에서 특정 거리(미터)만큼 동쪽으로 이동한 좌표의 경도 반환.
     */
    public static double lngOffsetMeters(double baseLat, double baseLng, double meters) {
        return baseLng + (meters / (111_320.0 * Math.cos(Math.toRadians(baseLat))));
    }
}

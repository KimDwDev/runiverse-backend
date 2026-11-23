package com.runiverse.running_service.application.recordcard.common;

import com.runiverse.running_service.application.running.common.PolylineDecoder;
import com.runiverse.running_service.application.running.port.out.RoutePoint;

import java.util.ArrayList;
import java.util.List;

// 기록의 구간 경로를 카드용 선 아트로 바꾼다 — 실제 위치를 지우고 모양만 남긴다(api-spec 8-1 「경로 선 아트」).
// 카드는 남에게도 보이므로 위경도를 싣지 않는다. 보는 사람이 본인이어도 같다 — 본인의 실제 경로는 6-1이 준다
public final class RouteArtConverter {

    // 전체 조각을 합친 점 수 상한 — 칸 하나에 선 하나를 긋기에 충분하고 응답이 작다
    static final int MAX_POINTS = 200;
    private static final double PRECISION = 10_000.0;   // 소수점 넷째 자리
    private static final double CENTER = 0.5;

    private RouteArtConverter() {

    }

    // 구간 순서(segment_number)대로 받은 폴리라인을 조각별 0~1 좌표로 바꾼다. 경로가 없으면 빈 목록이다
    public static List<List<Point>> convert(List<String> segmentPolylines) {
        List<List<RoutePoint>> segments = segmentPolylines.stream()
                .map(PolylineDecoder::decode)
                .filter(points -> !points.isEmpty())
                .toList();
        if (segments.isEmpty()) {
            return List.of();
        }

        // 1. 경로 전체를 감싸는 사각형
        double minLatitude = Double.MAX_VALUE;
        double maxLatitude = -Double.MAX_VALUE;
        double minLongitude = Double.MAX_VALUE;
        double maxLongitude = -Double.MAX_VALUE;
        for (List<RoutePoint> segment : segments) {
            for (RoutePoint point : segment) {
                minLatitude = Math.min(minLatitude, point.latitude());
                maxLatitude = Math.max(maxLatitude, point.latitude());
                minLongitude = Math.min(minLongitude, point.longitude());
                maxLongitude = Math.max(maxLongitude, point.longitude());
            }
        }

        // 2. 경도 1도는 위도가 높을수록 짧다 — 중심 위도의 cos을 곱해 가로·세로를 같은 거리 비율로 맞춘다
        double longitudeScale = Math.cos(Math.toRadians((minLatitude + maxLatitude) / 2));
        double width = (maxLongitude - minLongitude) * longitudeScale;
        double height = maxLatitude - minLatitude;
        double span = Math.max(width, height);

        // 3. 긴 변이 0~1을 채우고 짧은 변은 가운데 놓인다. y는 북쪽이 위(화면 좌표라 남쪽으로 커진다)
        double xOffset = (span - width) / 2;
        double yOffset = (span - height) / 2;
        List<List<Point>> art = new ArrayList<>();
        for (List<RoutePoint> segment : reduce(segments)) {
            List<Point> points = new ArrayList<>(segment.size());
            for (RoutePoint point : segment) {
                if (span == 0) {
                    // 한 점에 머문 경로 — 크기가 없어 가운데 하나로 둔다
                    points.add(new Point(CENTER, CENTER));
                    continue;
                }
                double x = ((point.longitude() - minLongitude) * longitudeScale + xOffset) / span;
                double y = ((maxLatitude - point.latitude()) + yOffset) / span;
                points.add(new Point(round(x), round(y)));
            }
            art.add(points);
        }
        return art;
    }

    // 점이 상한을 넘으면 조각마다 점 수에 비례해 고르게 남긴다 — 조각의 첫·끝 점(시작·도착·끊긴 곳)은 늘 남긴다.
    // 조각이 아주 많아 첫·끝만으로도 상한을 넘으면 첫·끝만 남긴다
    private static List<List<RoutePoint>> reduce(List<List<RoutePoint>> segments) {
        int total = segments.stream().mapToInt(List::size).sum();
        if (total <= MAX_POINTS) {
            return segments;
        }
        List<List<RoutePoint>> reduced = new ArrayList<>(segments.size());
        for (List<RoutePoint> segment : segments) {
            int budget = Math.max(Math.min(segment.size(), 2), segment.size() * MAX_POINTS / total);
            reduced.add(sample(segment, budget));
        }
        return reduced;
    }

    // 첫 점부터 끝 점까지 같은 간격으로 budget개를 고른다
    private static List<RoutePoint> sample(List<RoutePoint> segment, int budget) {
        if (segment.size() <= budget) {
            return segment;
        }
        if (budget == 1) {
            return List.of(segment.getFirst());
        }
        List<RoutePoint> sampled = new ArrayList<>(budget);
        int last = segment.size() - 1;
        for (int i = 0; i < budget; i++) {
            sampled.add(segment.get((int) Math.round((double) i * last / (budget - 1))));
        }
        return sampled;
    }

    private static double round(double value) {
        return Math.round(value * PRECISION) / PRECISION;
    }

    // 선 아트의 점 하나 — x는 동쪽으로, y는 남쪽으로 커진다(0~1)
    public record Point(double x, double y) {

    }
}

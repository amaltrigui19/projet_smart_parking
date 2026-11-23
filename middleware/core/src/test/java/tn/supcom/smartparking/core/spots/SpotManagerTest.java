package tn.supcom.smartparking.core.spots;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tn.supcom.smartparking.core.exception.InvalidRequestException;
import tn.supcom.smartparking.core.exception.NotFoundException;
import tn.supcom.smartparking.core.model.Point;
import tn.supcom.smartparking.core.model.Reservation;
import tn.supcom.smartparking.core.model.ReservationState;
import tn.supcom.smartparking.core.model.Spot;
import tn.supcom.smartparking.core.model.SpotState;
import tn.supcom.smartparking.core.model.SpotType;
import tn.supcom.smartparking.core.persistence.ReservationRepository;
import tn.supcom.smartparking.core.persistence.SpotRepository;

/** SpotManager with in-memory repositories: no MongoDB needed, thanks to the repository interfaces. */
class SpotManagerTest {

    private static final Instant NOW = Instant.parse("2026-11-23T10:00:00Z");
    private static final List<Point> SQUARE = List.of(new Point(0, 0), new Point(10, 0), new Point(10, 10));

    private final InMemorySpots spots = new InMemorySpots();
    private final InMemoryReservations reservations = new InMemoryReservations();
    private final List<SpotStateChanged> sent = new ArrayList<>();
    private SpotManager manager;

    @BeforeEach
    void setUp() {
        spots.save(spot("A1", SpotType.ACCESSIBLE, SpotState.FREE, null, 120));
        spots.save(spot("A2", SpotType.STANDARD, SpotState.OCCUPIED, NOW.minusSeconds(30 * 60), 120));
        spots.save(spot("B3", SpotType.STANDARD, SpotState.FREE, null, 120));
        spots.save(spot("B4", SpotType.ELECTRIC, SpotState.OCCUPIED, NOW.minusSeconds(61 * 60), 60));
        spots.save(new Spot("Z9", "P2", SpotType.STANDARD, 120, SQUARE, null, null, SpotState.FREE, null));
        reservations.open.add(Reservation.create("u_6543a1f0aabbccddeeff0011", "B3", NOW.minusSeconds(120), 15));
        manager = new SpotManager(spots, reservations, "P1", Clock.fixed(NOW, ZoneOffset.UTC), sent::add);
    }

    private static Spot spot(String id, SpotType type, SpotState state, Instant since, int maxMin) {
        return new Spot(id, "P1", type, maxMin, SQUARE, null, id, state, since);
    }

    @Test
    void listsOnlyTheConfiguredParkingSortedById() {
        assertEquals(List.of("A1", "A2", "B3", "B4"), manager.list().stream().map(SpotView::id).toList());
    }

    @Test
    void freeSpotWithOpenReservationIsShownReserved() {
        assertEquals(SpotState.RESERVED, manager.get("B3").state());
        assertEquals(SpotState.FREE, manager.get("A1").state());
    }

    @Test
    void summaryCountsEachState() {
        SpotsSummary s = SpotsSummary.of(manager.list());
        assertEquals(4, s.total());
        assertEquals(1, s.free());
        assertEquals(2, s.occupied());
        assertEquals(1, s.reserved());
    }

    @Test
    void overdueWhenOccupiedLongerThanMaxDuration() {
        assertFalse(manager.get("A2").overdue());   // 30 min of 120
        assertTrue(manager.get("B4").overdue());    // 61 min of 60
        assertFalse(manager.get("A1").overdue());   // free
    }

    @Test
    void unknownOrOtherParkingSpotIsNotFound() {
        assertThrows(NotFoundException.class, () -> manager.get("X1"));
        assertThrows(NotFoundException.class, () -> manager.get("Z9"));
    }

    @Test
    void stateChangeIsSavedAndPushed() {
        SpotView v = manager.updateState("A1", SpotState.OCCUPIED, null);
        assertEquals(SpotState.OCCUPIED, v.state());
        assertEquals(NOW, v.since());
        assertEquals(SpotState.OCCUPIED, spots.findById("A1").orElseThrow().state());
        assertEquals(List.of(new SpotStateChanged("A1", SpotState.OCCUPIED, NOW)), sent);
    }

    @Test
    void reservedSpotThatBecomesFreeIsPushedAsReserved() {
        manager.updateState("B3", SpotState.FREE, NOW);
        assertEquals(SpotState.RESERVED, sent.get(0).state());
    }

    @Test
    void reservedCannotComeFromThePi() {
        assertThrows(InvalidRequestException.class, () -> manager.updateState("A1", SpotState.RESERVED, null));
        assertTrue(sent.isEmpty());
    }

    // ---------- In-memory repositories ----------

    private static final class InMemorySpots implements SpotRepository {
        private final Map<String, Spot> byId = new LinkedHashMap<>();

        @Override
        public List<Spot> findByParking(String parkingId) {
            return byId.values().stream().filter(s -> s.parkingId().equals(parkingId))
                    .sorted(Comparator.comparing(Spot::id)).toList();
        }

        @Override
        public Optional<Spot> findById(String id) {
            return Optional.ofNullable(byId.get(id));
        }

        @Override
        public void save(Spot spot) {
            byId.put(spot.id(), spot);
        }

        @Override
        public void replacePolygons(String parkingId, List<Spot> list) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean updateState(String id, SpotState state, Instant since) {
            Spot s = byId.get(id);
            if (s == null) {
                return false;
            }
            byId.put(id, new Spot(s.id(), s.parkingId(), s.type(), s.maxDurationMin(), s.polygon(),
                    s.planRect(), s.graphNode(), state, since));
            return true;
        }
    }

    private static final class InMemoryReservations implements ReservationRepository {
        private final List<Reservation> open = new ArrayList<>();

        @Override
        public Reservation create(Reservation reservation) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<Reservation> findById(String id) {
            return Optional.empty();
        }

        @Override
        public List<Reservation> list(String userId, ReservationState state, int limit) {
            return List.of();
        }

        @Override
        public Optional<Reservation> findOpenByUser(String userId) {
            return open.stream().filter(r -> r.userId().equals(userId)).findFirst();
        }

        @Override
        public Optional<Reservation> findOpenBySpot(String spotId) {
            return open.stream().filter(r -> r.spotId().equals(spotId)).findFirst();
        }

        @Override
        public List<Reservation> findOpen() {
            return List.copyOf(open);
        }

        @Override
        public boolean update(Reservation reservation, ReservationState expectedState) {
            return false;
        }
    }
}

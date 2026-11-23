package tn.supcom.smartparking.core.spots;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import tn.supcom.smartparking.core.config.ParkingSettings;
import tn.supcom.smartparking.core.exception.InvalidRequestException;
import tn.supcom.smartparking.core.exception.NotFoundException;
import tn.supcom.smartparking.core.model.Reservation;
import tn.supcom.smartparking.core.model.Spot;
import tn.supcom.smartparking.core.model.SpotState;
import tn.supcom.smartparking.core.persistence.ReservationRepository;
import tn.supcom.smartparking.core.persistence.SpotRepository;

/**
 * Business logic of the spots map (contract §2.6): current state of every spot, "reserved" computed
 * from the open reservations, and state changes pushed to the WebSocket clients.
 * The REST resource and the WebSocket endpoint only delegate here.
 */
@ApplicationScoped
public class SpotManager {

    @Inject
    SpotRepository spotRepository;

    @Inject
    ReservationRepository reservationRepository;

    @Inject
    ParkingSettings settings;

    @Inject
    Event<SpotStateChanged> events;

    private Clock clock = Clock.systemUTC();
    private String parkingId;
    private Consumer<SpotStateChanged> notifier;

    public SpotManager() {
        // For CDI
    }

    /** For unit tests: no CDI, events go to {@code notifier}. */
    public SpotManager(SpotRepository spots, ReservationRepository reservations, String parkingId,
                       Clock clock, Consumer<SpotStateChanged> notifier) {
        this.spotRepository = spots;
        this.reservationRepository = reservations;
        this.parkingId = parkingId;
        this.clock = clock;
        this.notifier = notifier;
    }

    @PostConstruct
    void init() {
        parkingId = settings.parkingId();
        notifier = events::fire;
    }

    /** GET /api/spots: every spot of the parking, sorted by id. */
    public List<SpotView> list() {
        Set<String> reserved = reservedSpotIds();
        Instant now = clock.instant();
        List<SpotView> views = new ArrayList<>();
        for (Spot s : spotRepository.findByParking(parkingId)) {
            views.add(view(s, reserved.contains(s.id()), now));
        }
        return views;
    }

    /** GET /api/spots/{id}. */
    public SpotView get(String id) {
        Spot spot = find(id);
        return view(spot, reservationRepository.findOpenBySpot(id).isPresent(), clock.instant());
    }

    /**
     * New state reported by the Pi (MQTT, sprint 5) or by the simulation route: saves it and notifies the
     * WebSocket clients with the displayed state (a free spot with an open reservation is shown "reserved").
     */
    public SpotView updateState(String id, SpotState state, Instant since) {
        if (state != SpotState.FREE && state != SpotState.OCCUPIED) {
            throw new InvalidRequestException("L'état doit être free ou occupied (reserved est calculé par le serveur).");
        }
        find(id);
        Instant when = since == null ? clock.instant() : since;
        if (!spotRepository.updateState(id, state, when)) {
            throw new NotFoundException("Place inconnue : " + id);
        }
        SpotView view = get(id);
        notifier.accept(new SpotStateChanged(view.id(), view.state(), view.since()));
        return view;
    }

    private Spot find(String id) {
        return spotRepository.findById(id)
                .filter(s -> s.parkingId().equals(parkingId))
                .orElseThrow(() -> new NotFoundException("Place inconnue : " + id));
    }

    private Set<String> reservedSpotIds() {
        Set<String> ids = new HashSet<>();
        for (Reservation r : reservationRepository.findOpen()) {
            ids.add(r.spotId());
        }
        return ids;
    }

    /** Displayed state and overdue flag of a spot at instant {@code now}. */
    static SpotView view(Spot s, boolean hasOpenReservation, Instant now) {
        SpotState state = s.state() == SpotState.FREE && hasOpenReservation ? SpotState.RESERVED : s.state();
        boolean overdue = s.state() == SpotState.OCCUPIED && s.since() != null
                && s.since().plusSeconds(60L * s.maxDurationMin()).isBefore(now);
        return new SpotView(s.id(), s.type(), state, s.since(), s.maxDurationMin(), overdue);
    }
}

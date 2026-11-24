package tn.supcom.smartparking.core.spots;

import java.util.List;
import tn.supcom.smartparking.core.model.SpotState;

/** Counts of the spots by state (contract §2.6: summary {total, free, occupied, reserved}). */
public record SpotsSummary(int total, int free, int occupied, int reserved) {

    public static SpotsSummary of(List<SpotView> spots) {
        int free = 0;
        int occupied = 0;
        int reserved = 0;
        for (SpotView s : spots) {
            switch (s.state()) {
                case FREE -> free++;
                case OCCUPIED -> occupied++;
                case RESERVED -> reserved++;
            }
        }
        return new SpotsSummary(spots.size(), free, occupied, reserved);
    }

    /** Helper for tests and logs. */
    public int count(SpotState state) {
        return switch (state) {
            case FREE -> free;
            case OCCUPIED -> occupied;
            case RESERVED -> reserved;
        };
    }
}

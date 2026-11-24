/**
 * Core (business domain): spots, reservations, guidance, alerts, MongoDB access, MQTT client.
 * Does not depend on the REST API nor on the IAM module. Contract: docs/contrats.md, sections 2 and 5.
 * <ul>
 *   <li>{@code model} — domain records (Parking, Spot, Reservation, Event, Permission…)</li>
 *   <li>{@code persistence} — repository interfaces and their MongoDB implementations</li>
 *   <li>{@code calibration} — calibration manager, validator and mapper</li>
 *   <li>{@code spots} — spots map: displayed states, summary, state changes (WebSocket)</li>
 *   <li>{@code exception} — business exceptions, mapped to HTTP errors by the API</li>
 *   <li>{@code config} — settings read through MicroProfile Config</li>
 * </ul>
 */
package tn.supcom.smartparking.core;

package tr.id4.mediahub.nativecockpit.domain.model

/** A turn instruction and coordinate MUST originate in a real routing engine.
 * The id must stay stable between subsequent GPS updates of the same step.
 */
data class NavigationManeuver(
    val id: String,
    val instructionTr: String,
    val location: GeoCoordinate
)

package tourrouteplanner.util;

import com.google.gson.JsonObject;

/**
 * Utility class for formatting turn-by-turn navigation instructions.
 * Converts OSRM maneuver data into human-readable text.
 */
public class InstructionFormatter {

    /**
     * Generates a human-readable instruction from OSRM maneuver data.
     *
     * @param maneuverObj The maneuver JSON object from OSRM response.
     * @param streetName  The name of the street (can be empty).
     * @param rotaryName  The name of the roundabout/rotary (can be empty).
     * @return A formatted instruction string.
     */
    public String generateInstruction(JsonObject maneuverObj, String streetName, String rotaryName) {
        if (maneuverObj == null) {
            return "";
        }

        String type = maneuverObj.has("type") ? maneuverObj.get("type").getAsString() : "";
        String modifier = maneuverObj.has("modifier") ? maneuverObj.get("modifier").getAsString() : "";

        StringBuilder instruction = new StringBuilder();

        switch (type.toLowerCase()) {
            case "depart":
                instruction.append("Depart");
                if (!modifier.isEmpty()) {
                    instruction.append(" ").append(translateModifier(modifier));
                }
                if (!streetName.isEmpty()) {
                    instruction.append(" onto ").append(streetName);
                }
                break;

            case "turn":
                instruction.append("Turn");
                if (!modifier.isEmpty()) {
                    instruction.append(" ").append(translateModifier(modifier));
                } else {
                    instruction.append(" in unknown direction");
                }
                if (!streetName.isEmpty()) {
                    instruction.append(" onto ").append(streetName);
                }
                break;

            case "continue":
                instruction.append("Continue straight");
                if (!modifier.isEmpty() && !modifier.equalsIgnoreCase("straight")) {
                    instruction.append(" ").append(translateModifier(modifier));
                }
                if (!streetName.isEmpty()) {
                    instruction.append(" on ").append(streetName);
                }
                break;

            case "new name":
                instruction.append("Continue onto new road");
                if (!modifier.isEmpty()) {
                    instruction.append(" ").append(translateModifier(modifier));
                }
                if (!streetName.isEmpty()) {
                    instruction.append(": ").append(streetName);
                }
                break;

            case "arrive":
                instruction.append("Arrive");
                if (!streetName.isEmpty()) {
                    instruction.append(" at ").append(streetName);
                }
                if (!modifier.isEmpty() && (modifier.equalsIgnoreCase("left") || modifier.equalsIgnoreCase("right"))) {
                    instruction.append(" (on the ").append(translateModifier(modifier)).append(")");
                }
                break;

            case "merge":
                instruction.append("Merge");
                if (!modifier.isEmpty()) {
                    instruction.append(" ").append(translateModifier(modifier));
                }
                if (!streetName.isEmpty()) {
                    instruction.append(" onto ").append(streetName);
                }
                break;

            case "fork":
                instruction.append("Take the fork");
                if (!modifier.isEmpty()) {
                    instruction.append(" ").append(translateModifier(modifier));
                } else {
                    instruction.append(" unknown");
                }
                if (!streetName.isEmpty()) {
                    instruction.append(" on ").append(streetName);
                }
                break;

            case "end of road":
                instruction.append("At end of road, turn");
                if (!modifier.isEmpty()) {
                    instruction.append(" ").append(translateModifier(modifier));
                }
                if (!streetName.isEmpty()) {
                    instruction.append(" onto ").append(streetName);
                }
                break;

            case "use lane":
                instruction.append("Use lane");
                if (!modifier.isEmpty()) {
                    instruction.append(" ").append(translateModifier(modifier));
                }
                if (!streetName.isEmpty()) {
                    instruction.append(" on ").append(streetName);
                }
                break;

            case "roundabout":
            case "rotary":
                instruction.append("Enter ");
                if (rotaryName != null && !rotaryName.isEmpty()) {
                    instruction.append(rotaryName);
                } else {
                    instruction.append("roundabout");
                }
                if (maneuverObj.has("exit") && !maneuverObj.get("exit").isJsonNull()) {
                    instruction.append(" and take exit ").append(maneuverObj.get("exit").getAsInt());
                }
                if (streetName != null && !streetName.isEmpty()
                        && (rotaryName == null || rotaryName.isEmpty() || !streetName.equals(rotaryName))) {
                    instruction.append(" onto ").append(streetName);
                }
                break;

            case "exit roundabout":
            case "exit rotary":
                instruction.append("Exit ");
                if (rotaryName != null && !rotaryName.isEmpty()) {
                    instruction.append(rotaryName);
                } else {
                    instruction.append("roundabout");
                }
                if (!modifier.isEmpty()) {
                    instruction.append(" ").append(translateModifier(modifier));
                }
                if (streetName != null && !streetName.isEmpty()) {
                    instruction.append(" onto ").append(streetName);
                }
                break;

            default:
                if (!type.isEmpty()) {
                    instruction.append(capitalize(type));
                }
                if (!modifier.isEmpty()) {
                    instruction.append(" ").append(translateModifier(modifier));
                }
                if (streetName != null && !streetName.isEmpty()) {
                    instruction.append(" on ").append(streetName);
                }
                if (instruction.length() == 0) {
                    return "";
                }
                break;
        }

        return instruction.toString().trim();
    }

    /**
     * Translates OSRM modifier values to human-readable text.
     *
     * @param osrmModifier The modifier from OSRM (e.g., "uturn", "sharp right").
     * @return Human-readable modifier text.
     */
    public String translateModifier(String osrmModifier) {
        if (osrmModifier == null) {
            return "";
        }

        switch (osrmModifier.toLowerCase()) {
            case "uturn":
                return "U-turn";
            case "sharp right":
                return "sharp right";
            case "right":
                return "right";
            case "slight right":
                return "slight right";
            case "straight":
                return "straight";
            case "slight left":
                return "slight left";
            case "left":
                return "left";
            case "sharp left":
                return "sharp left";
            default:
                return osrmModifier;
        }
    }

    /**
     * Capitalizes the first letter of a string.
     *
     * @param str The string to capitalize.
     * @return The capitalized string.
     */
    private String capitalize(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }
}

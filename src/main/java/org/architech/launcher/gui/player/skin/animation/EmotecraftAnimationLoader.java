// SPDX-FileCopyrightText: 2026 Raijin2312
// SPDX-License-Identifier: GPL-3.0-only

package org.architech.launcher.gui.player.skin.animation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

final class EmotecraftAnimationLoader {
    private static final double TICKS_PER_SECOND = 20.0;
    private static final ObjectMapper JSON = new ObjectMapper();

    private EmotecraftAnimationLoader() {}

    static SkinAnimationClip load(String id, String resource) {
        try (InputStream stream = EmotecraftAnimationLoader.class.getResourceAsStream(resource)) {
            if (stream == null) {
                throw new IllegalArgumentException("Animation resource not found: " + resource);
            }
            return parse(id, JSON.readTree(stream));
        } catch (IOException error) {
            throw new IllegalArgumentException("Could not read animation resource: " + resource, error);
        }
    }

    static SkinAnimationClip parse(String id, JsonNode root) {
        JsonNode emote = root.path("emote");
        boolean degrees = flexibleBoolean(emote.path("degrees"), false);
        boolean easeBefore = flexibleBoolean(emote.path("easeBeforeKeyframe"), false);
        EnumMap<SkinBone, SkinAnimationClip.BoneTracks> bones = new EnumMap<>(SkinBone.class);
        double lastTick = Math.max(emote.path("endTick").asDouble(), emote.path("stopTick").asDouble());

        for (JsonNode move : emote.path("moves")) {
            double tick = move.path("tick").asDouble();
            lastTick = Math.max(lastTick, tick);
            Easing easing = Easing.parse(move.path("easing").asText("LINEAR"));
            var fields = move.fields();
            while (fields.hasNext()) {
                var field = fields.next();
                SkinBone bone = parseBone(field.getKey());
                if (bone == null || !field.getValue().isObject()) {
                    continue;
                }
                SkinAnimationClip.BoneTracks tracks = bones.computeIfAbsent(
                        bone,
                        ignored -> createTracks(bone, easeBefore));
                addChannels(tracks, field.getValue(), tick / TICKS_PER_SECOND, easing, degrees);
            }
        }

        return new SkinAnimationClip(id, Math.max(0.05, lastTick / TICKS_PER_SECOND), bones);
    }

    private static void addChannels(
            SkinAnimationClip.BoneTracks tracks,
            JsonNode values,
            double time,
            Easing easing,
            boolean degrees) {
        addAngle(tracks.pitch, values, "pitch", time, easing, degrees, 1.0);
        addAngle(tracks.yaw, values, "yaw", time, easing, degrees, 1.0);
        addAngle(tracks.roll, values, "roll", time, easing, degrees, 1.0);
        addValue(tracks.x, values, "x", time, easing);
        addValue(tracks.y, values, "y", time, easing);
        addValue(tracks.z, values, "z", time, easing);
    }

    private static void addAngle(
            KeyframeTrack track,
            JsonNode values,
            String name,
            double time,
            Easing easing,
            boolean degrees,
            double axisDirection) {
        if (!values.has(name)) {
            return;
        }
        double angle = values.path(name).asDouble();
        track.add(time, axisDirection * (degrees ? angle : Math.toDegrees(angle)), easing);
    }

    private static void addValue(
            KeyframeTrack track,
            JsonNode values,
            String name,
            double time,
            Easing easing) {
        if (values.has(name)) {
            track.add(time, values.path(name).asDouble(), easing);
        }
    }

    private static SkinAnimationClip.BoneTracks createTracks(SkinBone bone, boolean easeBefore) {
        double[] base = switch (bone) {
            case BODY, HEAD, TORSO -> new double[] {0.0, 0.0, 0.0};
            case RIGHT_ARM -> new double[] {-5.0, 2.0, 0.0};
            case LEFT_ARM -> new double[] {5.0, 2.0, 0.0};
            case RIGHT_LEG -> new double[] {-1.9, 12.0, -0.1};
            case LEFT_LEG -> new double[] {1.9, 12.0, -0.1};
        };
        return new SkinAnimationClip.BoneTracks(base[0], base[1], base[2], easeBefore);
    }

    private static SkinBone parseBone(String value) {
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "body" -> SkinBone.BODY;
            case "head" -> SkinBone.HEAD;
            case "torso" -> SkinBone.TORSO;
            case "rightarm" -> SkinBone.RIGHT_ARM;
            case "leftarm" -> SkinBone.LEFT_ARM;
            case "rightleg" -> SkinBone.RIGHT_LEG;
            case "leftleg" -> SkinBone.LEFT_LEG;
            default -> null;
        };
    }

    private static boolean flexibleBoolean(JsonNode value, boolean fallback) {
        if (value.isBoolean()) {
            return value.asBoolean();
        }
        if (value.isTextual()) {
            return Boolean.parseBoolean(value.asText());
        }
        return fallback;
    }
}

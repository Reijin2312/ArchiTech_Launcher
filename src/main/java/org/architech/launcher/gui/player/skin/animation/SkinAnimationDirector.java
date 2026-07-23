// SPDX-FileCopyrightText: 2026 Raijin2312
// SPDX-License-Identifier: GPL-3.0-only

package org.architech.launcher.gui.player.skin.animation;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;

/** Selects, blends, and rate-limits character animations. */
public final class SkinAnimationDirector {
    private static final double BLEND_IN_SECONDS = 0.38;
    private static final double BLEND_OUT_SECONDS = 0.48;
    private static final double MIN_IDLE_SECONDS = 6.0;
    private static final double MAX_IDLE_SECONDS = 14.0;

    private final SplittableRandom random;
    private final List<AnimationEntry> entries;
    private final Map<String, Double> cooldownUntil = new HashMap<>();
    private final Deque<String> recent = new ArrayDeque<>();

    private Playback active;
    private AnimationPose lastPose = new AnimationPose();
    private double clock;
    private double nextAutomaticAt;

    public SkinAnimationDirector() {
        this(new SplittableRandom());
    }

    SkinAnimationDirector(long seed) {
        this(new SplittableRandom(seed));
    }

    private SkinAnimationDirector(SplittableRandom random) {
        this.random = random;
        entries = List.of(
                entry("waving", 12, 16, 28),
                entry("clap", 4, 14, 42),
                entry("palm", 8, 12, 34),
                entry("here", 10, 14, 30),
                entry("point", 10, 14, 30),
                entry("backflip", 1, 6, 120));
        nextAutomaticAt = randomIdleDelay();
    }

    public AnimationPose update(double deltaSeconds) {
        double delta = Math.max(0.0, Math.min(0.1, deltaSeconds));
        clock += delta;

        if (active != null) {
            active.elapsed += delta;
            if (active.elapsed >= active.entry.clip.durationSeconds()) {
                active = null;
                nextAutomaticAt = clock + randomIdleDelay();
            }
        } else if (clock >= nextAutomaticAt) {
            chooseAndPlay(false);
        }

        AnimationPose idle = idlePose(clock);
        if (active == null) {
            lastPose = idle;
            return lastPose;
        }

        double remaining = active.entry.clip.durationSeconds() - active.elapsed;
        AnimationPose clipPose = active.entry.clip.sample(active.elapsed);
        if (active.elapsed < BLEND_IN_SECONDS) {
            lastPose = AnimationPose.blend(
                    active.transitionFrom,
                    clipPose,
                    smoothStep(active.elapsed / BLEND_IN_SECONDS));
        } else if (remaining < BLEND_OUT_SECONDS) {
            lastPose = AnimationPose.blend(
                    clipPose,
                    idle,
                    1.0 - smoothStep(remaining / BLEND_OUT_SECONDS));
        } else {
            lastPose = clipPose;
        }
        return lastPose;
    }

    public void triggerGreeting() {
        play(find("waving"));
    }

    public void triggerInteraction() {
        chooseAndPlay(true);
    }

    public void reset() {
        active = null;
        nextAutomaticAt = clock + randomIdleDelay();
    }

    String activeAnimationId() {
        return active == null ? null : active.entry.clip.id();
    }

    private void chooseAndPlay(boolean interaction) {
        List<AnimationEntry> candidates = new ArrayList<>();
        int totalWeight = 0;
        for (AnimationEntry entry : entries) {
            int weight = interaction ? entry.interactionWeight : entry.automaticWeight;
            if (weight <= 0
                    || recent.contains(entry.clip.id())
                    || cooldownUntil.getOrDefault(entry.clip.id(), 0.0) > clock) {
                continue;
            }
            candidates.add(entry);
            totalWeight += weight;
        }

        if (candidates.isEmpty()) {
            recent.clear();
            nextAutomaticAt = clock + 1.5;
            return;
        }

        int selected = random.nextInt(totalWeight);
        for (AnimationEntry entry : candidates) {
            selected -= interaction ? entry.interactionWeight : entry.automaticWeight;
            if (selected < 0) {
                play(entry);
                return;
            }
        }
    }

    private void play(AnimationEntry entry) {
        if (entry == null) {
            return;
        }
        active = new Playback(entry, lastPose);
        cooldownUntil.put(entry.clip.id(), clock + entry.cooldownSeconds);
        recent.addLast(entry.clip.id());
        while (recent.size() > 2) {
            recent.removeFirst();
        }
    }

    private AnimationEntry find(String id) {
        return entries.stream().filter(entry -> entry.clip.id().equals(id)).findFirst().orElse(null);
    }

    private AnimationEntry entry(
            String id,
            int automaticWeight,
            int interactionWeight,
            double cooldownSeconds) {
        SkinAnimationClip clip = EmotecraftAnimationLoader.load(
                id,
                "/animations/emotecraft/" + id + ".json");
        return new AnimationEntry(clip, automaticWeight, interactionWeight, cooldownSeconds);
    }

    private double randomIdleDelay() {
        return MIN_IDLE_SECONDS + random.nextDouble() * (MAX_IDLE_SECONDS - MIN_IDLE_SECONDS);
    }

    private static AnimationPose idlePose(double time) {
        double baseAngle = Math.toDegrees(Math.PI * 0.02);
        double leftRoll = -Math.toDegrees(Math.cos(time * 2.0) * 0.03) - baseAngle;
        double rightRoll = -Math.toDegrees(Math.cos(time * 2.0 + Math.PI) * 0.03) + baseAngle;
        return new AnimationPose()
                .set(SkinBone.LEFT_ARM, new BoneTransform(0, 0, leftRoll, 0, 0, 0))
                .set(SkinBone.RIGHT_ARM, new BoneTransform(0, 0, rightRoll, 0, 0, 0));
    }

    private static double smoothStep(double value) {
        double clamped = Math.max(0.0, Math.min(1.0, value));
        return clamped * clamped * (3.0 - 2.0 * clamped);
    }

    private record AnimationEntry(
            SkinAnimationClip clip,
            int automaticWeight,
            int interactionWeight,
            double cooldownSeconds) {}

    private static final class Playback {
        private final AnimationEntry entry;
        private final AnimationPose transitionFrom;
        private double elapsed;

        private Playback(AnimationEntry entry, AnimationPose transitionFrom) {
            this.entry = entry;
            this.transitionFrom = transitionFrom;
        }
    }
}

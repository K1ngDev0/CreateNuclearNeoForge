package net.nuclearteam.createnuclear.infrastructure.config;

import net.createmod.catnip.config.ConfigBase;

public class CExplose extends ConfigBase {
    public final ConfigInt size = i(24, "Size of the reactor explosion");
    public final ConfigInt type = i(2, 0, 2, "Type of explosion", Comments.type);
    public final ConfigInt time = i(600, 100, 1200, "Duration before exploration", Comments.explosionTime, Comments.hintExplosion);

    public final ConfigBool enableRadiationClouds = b(true, "enableRadiationClouds", Comments.enableRadiationClouds);
    public final ConfigInt cloudCount = i(8, 1, 24, "How many radioactive clouds spawn", Comments.cloudCount);
    public final ConfigInt cloudLifetimeTicks = i(2400, 200, 12000, "Lifetime of radioactive clouds in ticks", Comments.cloudLifetime);
    public final ConfigInt cloudRadius = i(8, 2, 24, "Base radius of radioactive clouds", Comments.cloudRadius);
    public final ConfigInt cloudDriftSpeed = i(14, 0, 60, "Cloud drift speed (percent of a block per tick)", Comments.cloudDrift);

    public final ConfigBool enableLingeringSmoke = b(true, "enableLingeringSmoke", Comments.enableLingeringSmoke);
    public final ConfigInt smokeLifetimeTicks = i(3600, 200, 20000, "How long aftermath smoke stays active in ticks", Comments.smokeLifetime);

    public final ConfigBool enableUraniumLeak = b(true, "enableUraniumLeak", Comments.enableUraniumLeak);
    public final ConfigInt uraniumLeakPuddles = i(14, 0, 64, "How many uranium puddles are spawned", Comments.uraniumLeakPuddles);
    public final ConfigInt uraniumLeakDrops = i(18, 0, 128, "How many uranium powder drops are ejected", Comments.uraniumLeakDrops);

    @Override
    public String getName() {
        return "Explosion Reactor";
    }

    private static class Comments {
        static String explosionTime = "Create Nuclear Explosion Time";
        static String hintExplosion = "300 ticks = 15 seconds";
        static String type = "Explanation: 0 = no explosion, 1 = current explosion, 2 = new explosion.";
        static String enableRadiationClouds = "Enable radioactive mushroom cloud hazards after spectacular explosions";
        static String cloudCount = "Higher values create larger fallout zones and more entity checks";
        static String cloudLifetime = "Clouds continue poisoning entities while alive";
        static String cloudRadius = "Base radius for each cloud; each cloud gets a small random offset";
        static String cloudDrift = "Cloud movement speed for random drifting clouds";
        static String enableLingeringSmoke = "If enabled, explosion aftermath keeps emitting smoke";
        static String smokeLifetime = "Duration of visual smoke after the blast";
        static String enableUraniumLeak = "If enabled, the explosion leaks liquid uranium and throws uranium powder";
        static String uraniumLeakPuddles = "Number of leaked liquid uranium spots";
        static String uraniumLeakDrops = "Number of radioactive item drops launched from the blast";
    }
}

package faygolover.rpperks.gametest;

import faygolover.rpperks.Rpperks;
import faygolover.rpperks.perk.PerkConsumers;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Предложения 06.11: у каждой особенности записано, кто даёт ей механику ({@link PerkConsumers}). */
@GameTestHolder(Rpperks.MODID)
@PrefixGameTestTemplate(false)
public final class PerkGameTests {
    private PerkGameTests() {}

    @GameTest(template = "platform")
    public static void everyPerkHasConsumer(GameTestHelper h) {
        var missing = PerkConsumers.unlisted();
        if (!missing.isEmpty()) h.fail("Не вписаны в PerkConsumers: " + missing);
        h.succeed();
    }
}

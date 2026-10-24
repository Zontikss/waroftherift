package io.github.waroftherift;

import com.mojang.logging.LogUtils;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

/**
 * Точка входа мода War of the Rift.
 *
 * <p>На этом этапе класс только подключает mod event bus и выполняет общую инициализацию.
 * Игровые системы (фракции, война, порталы и т. д.) добавляются на следующих этапах —
 * см. {@code docs/PLAN.md}. Здесь нет клиентского кода, поэтому класс безопасен на выделенном сервере.
 */
@Mod(WarOfTheRiftMod.MOD_ID)
public final class WarOfTheRiftMod {

    /** Должен совпадать с {@code mod_id} в {@code gradle.properties} и {@code mods.toml}. */
    public static final String MOD_ID = "waroftherift";

    public static final Logger LOGGER = LogUtils.getLogger();

    public WarOfTheRiftMod() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        modEventBus.addListener(this::onCommonSetup);
    }

    private void onCommonSetup(final FMLCommonSetupEvent event) {
        LOGGER.info("War of the Rift ({}) initialised: common setup complete", MOD_ID);
    }
}

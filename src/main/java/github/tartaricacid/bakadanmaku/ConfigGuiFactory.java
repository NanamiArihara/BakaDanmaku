package github.tartaricacid.bakadanmaku;

import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import cpw.mods.fml.client.IModGuiFactory;
import cpw.mods.fml.client.config.DummyConfigElement;
import cpw.mods.fml.client.config.GuiConfig;
import cpw.mods.fml.client.config.IConfigElement;
import github.tartaricacid.common.config.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

public class ConfigGuiFactory implements IModGuiFactory {
    protected Minecraft minecraft;

    public static class BakaDanmakuConfigGuiScreen extends GuiConfig {
        public BakaDanmakuConfigGuiScreen(GuiScreen parentScreen) {
            super(parentScreen, collectConfigElements(ConfigManager.getModConfigClasses(BakaDanmaku.MOD_ID)), BakaDanmaku.MOD_ID, false, false, BakaDanmaku.MOD_NAME);
        }
    }

    @Override
    public void initialize(Minecraft minecraftInstance)
    {
        this.minecraft = minecraftInstance;
    }

    @Override
    public Class<? extends GuiScreen> mainConfigGuiClass() {
        return BakaDanmakuConfigGuiScreen.class;
    }

    /**
     * Provides a ConfigElement derived from the annotation-based config system
     * @param configClass the class which contains the configuration
     * @return A ConfigElement based on the described category.
     */
    public static IConfigElement from(Class<?> configClass)
    {
        Config annotation = configClass.getAnnotation(Config.class);
        if (annotation == null)
            throw new RuntimeException(String.format("The class '%s' has no @Config annotation!", configClass.getName()));

        Configuration config = ConfigManager.getConfiguration(annotation.modid(), annotation.name());
        if (config == null)
        {
            String error = String.format("The configuration '%s' of mod '%s' isn't loaded with the ConfigManager!", annotation.name(), annotation.modid());
            throw new RuntimeException(error);
        }

        String name = Strings.isNullOrEmpty(annotation.name()) ? annotation.modid() : annotation.name();
        String langKey = name;
        Config.LangKey langKeyAnnotation = configClass.getAnnotation(Config.LangKey.class);
        if (langKeyAnnotation != null)
        {
            langKey = langKeyAnnotation.value();
        }

        if (annotation.category().isEmpty())
        {
            List<IConfigElement> elements = Lists.newArrayList();
            Set<String> catNames = config.getCategoryNames();
            for (String catName : catNames)
            {
                if (catName.isEmpty())
                    continue;
                ConfigCategory category = config.getCategory(catName);
                if (category.isChild())
                    continue;
                DummyConfigElement.DummyCategoryElement element = new DummyConfigElement.DummyCategoryElement(category.getName(), category.getLanguagekey(), new ConfigElement(category).getChildElements());
                element.setRequiresMcRestart(category.requiresMcRestart());
                element.setRequiresWorldRestart(category.requiresWorldRestart());
                elements.add(element);
            }

            return new DummyConfigElement.DummyCategoryElement(name, langKey, elements);
        }
        else
        {
            ConfigCategory category = config.getCategory(annotation.category());
            DummyConfigElement.DummyCategoryElement element = new DummyConfigElement.DummyCategoryElement(name, langKey, new ConfigElement(category).getChildElements());
            element.setRequiresMcRestart(category.requiresMcRestart());
            element.setRequiresWorldRestart(category.requiresWorldRestart());
            return element;
        }
    }

    private static List<IConfigElement> collectConfigElements(Class<?>[] configClasses)
    {
        List<IConfigElement> toReturn;
        if(configClasses.length == 1)
        {
            toReturn = from(configClasses[0]).getChildElements();
        }
        else
        {
            toReturn = new ArrayList<>();
            for(Class<?> clazz : configClasses)
            {
                toReturn.add(from(clazz));
            }
        }
        toReturn.sort(Comparator.comparing(e -> I18n.format(e.getLanguageKey())));
        return toReturn;
    }

    @Override
    public Set<RuntimeOptionCategoryElement> runtimeGuiCategories()
    {
        return null;
    }

    @Override
    public RuntimeOptionGuiHandler getHandlerFor(RuntimeOptionCategoryElement element) {
        return null;
    }
}

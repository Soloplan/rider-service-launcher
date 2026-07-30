package local.soloplan.tools.servicelauncher;

import com.intellij.openapi.util.IconLoader;
import com.intellij.util.IconUtil;

import javax.imageio.ImageIO;
import javax.swing.Icon;
import java.awt.Image;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

final class LauncherIcons {
    private static final int SERVICE_ICON_SIZE = 24;
    private static final float SVG_RENDER_SCALE = 4.0f;
    private static final Map<String, Icon> ICONS = new LinkedHashMap<>();
    private static final int CUSTOM_CACHE_LIMIT = 128;
    private static final Map<String, CustomIconCacheEntry> CUSTOM_CACHE =
        new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, CustomIconCacheEntry> eldest) {
                return size() > CUSTOM_CACHE_LIMIT;
            }
        };

    static {
        ICONS.put("service", load("/icons/service.svg"));
        ICONS.put("web", load("/icons/web.svg"));
        ICONS.put("dotnet", load("/icons/dotnet.svg"));
        ICONS.put("database", load("/icons/database.svg"));
        ICONS.put("worker", load("/icons/worker.svg"));
        ICONS.put("docker", load("/icons/docker.svg"));
    }

    private LauncherIcons() {
    }

    static Icon get(String key) {
        return ICONS.getOrDefault(key, ICONS.get("service"));
    }

    static Icon get(ServiceLauncherSettings.ServiceAppearance appearance) {
        if (appearance.customIconData == null || appearance.customIconData.isBlank()) {
            return get(appearance.icon);
        }
        synchronized (CUSTOM_CACHE) {
            CustomIconCacheEntry cached = CUSTOM_CACHE.get(appearance.itemId);
            if (cached != null
                && cached.name.equals(appearance.customIconName)
                && cached.data.equals(appearance.customIconData)) {
                return cached.icon;
            }
            Icon icon = loadCustom(appearance);
            CUSTOM_CACHE.put(appearance.itemId,
                new CustomIconCacheEntry(appearance.customIconName, appearance.customIconData, icon));
            return icon;
        }
    }

    static String[] keys() {
        return ICONS.keySet().toArray(String[]::new);
    }

    private static Icon load(String path) {
        return scaleIcon(IconLoader.getIcon(path, LauncherIcons.class), SERVICE_ICON_SIZE);
    }

    private static Icon loadCustom(ServiceLauncherSettings.ServiceAppearance appearance) {
        try {
            byte[] bytes = Base64.getDecoder().decode(appearance.customIconData);
            Image image;
            if (appearance.customIconName.toLowerCase().endsWith(".svg")) {
                image = loadSvg(bytes);
            } else {
                BufferedImage buffered = ImageIO.read(new ByteArrayInputStream(bytes));
                if (buffered == null) {
                    return get(appearance.icon);
                }
                image = buffered;
            }
            return new HighQualityImageIcon(image, SERVICE_ICON_SIZE, SERVICE_ICON_SIZE);
        } catch (Exception ignored) {
            return get(appearance.icon);
        }
    }

    private static Image loadSvg(byte[] bytes) throws Exception {
        Class<?> svgLoader = Class.forName("com.intellij.util.SVGLoader");
        Method load = svgLoader.getMethod("load", InputStream.class, float.class);
        return (Image) load.invoke(null, new ByteArrayInputStream(bytes), SVG_RENDER_SCALE);
    }

    static Icon scaleIcon(Icon source, int size) {
        int largestDimension = Math.max(1, Math.max(source.getIconWidth(), source.getIconHeight()));
        return IconUtil.scale(source, null, (float) size / largestDimension);
    }

    private record CustomIconCacheEntry(String name, String data, Icon icon) {
    }

    private static final class HighQualityImageIcon implements Icon {
        private final Image source;
        private final int width;
        private final int height;

        private HighQualityImageIcon(Image source, int maximumWidth, int maximumHeight) {
            this.source = source;
            int sourceWidth = Math.max(1, source.getWidth(null));
            int sourceHeight = Math.max(1, source.getHeight(null));
            double scale = Math.min((double) maximumWidth / sourceWidth, (double) maximumHeight / sourceHeight);
            this.width = Math.max(1, (int) Math.round(sourceWidth * scale));
            this.height = Math.max(1, (int) Math.round(sourceHeight * scale));
        }

        @Override
        public void paintIcon(java.awt.Component component, Graphics graphics, int x, int y) {
            Graphics2D graphics2D = (Graphics2D) graphics.create();
            try {
                graphics2D.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                graphics2D.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                graphics2D.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                graphics2D.drawImage(source, x, y, width, height, component);
            } finally {
                graphics2D.dispose();
            }
        }

        @Override
        public int getIconWidth() {
            return width;
        }

        @Override
        public int getIconHeight() {
            return height;
        }
    }
}

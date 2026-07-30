//-----------------------------------------------------------------------
// <copyright file="LauncherIcons.java" company="Soloplan GmbH">
// Copyright (c) Soloplan GmbH. All rights reserved.
// </copyright>
//-----------------------------------------------------------------------

package local.soloplan.tools.servicelauncher;

import com.intellij.openapi.util.IconLoader;
import com.intellij.util.IconUtil;

import javax.imageio.ImageIO;
import javax.swing.Icon;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Represents a launcher icons.
 */
final class LauncherIcons
{
  private static final int SERVICE_ICON_SIZE = 24;
  private static final float SVG_RENDER_SCALE = 4.0f;
  private static final Map<String, Icon> ICONS = new LinkedHashMap<>();
  private static final int CUSTOM_CACHE_LIMIT = 128;
  private static final Map<String, CustomIconCacheEntry> CUSTOM_CACHE =
    new LinkedHashMap<>(16, 0.75f, true)
    {
      /** {@inheritDoc} */
      @Override
      protected boolean removeEldestEntry(Map.Entry<String, CustomIconCacheEntry> eldest)
      {
        return size() > CUSTOM_CACHE_LIMIT;
      }
    };

  static
  {
    ICONS.put("service", load("/icons/service.svg"));
    ICONS.put("web", load("/icons/web.svg"));
    ICONS.put("dotnet", load("/icons/dotnet.svg"));
    ICONS.put("database", load("/icons/database.svg"));
    ICONS.put("worker", load("/icons/worker.svg"));
    ICONS.put("docker", load("/icons/docker.svg"));
  }

  /**
   * Creates a new {@code LauncherIcons} instance.
   */
  private LauncherIcons()
  {
  }

  /**
   * Returns the result of get.
   *
   * @param key the key
   * @return the get result
   */
  static Icon get(String key)
  {
    return ICONS.getOrDefault(key, ICONS.get("service"));
  }

  /**
   * Returns the result of get.
   *
   * @param launcherConfiguration the launcher configuration
   * @return the get result
   */
  static Icon get(ServiceLauncherSettings.LauncherConfiguration launcherConfiguration)
  {
    if (launcherConfiguration.customIconData == null || launcherConfiguration.customIconData.isBlank())
    {
      return get(launcherConfiguration.icon);
    }
    synchronized (CUSTOM_CACHE)
    {
      CustomIconCacheEntry cached = CUSTOM_CACHE.get(launcherConfiguration.itemId);
      if (cached != null
        && cached.name().equals(launcherConfiguration.customIconName)
        && cached.data().equals(launcherConfiguration.customIconData))
        {
        return cached.icon();
        }
      Icon icon = loadCustom(launcherConfiguration);
      CUSTOM_CACHE.put(launcherConfiguration.itemId,
        new CustomIconCacheEntry(launcherConfiguration.customIconName, launcherConfiguration.customIconData, icon));
      return icon;
    }
  }

  /**
   * Returns the result of keys.
   *
   * @return the keys result
   */
  static String[] keys()
  {
    return ICONS.keySet().toArray(String[]::new);
  }

  /**
   * Loads the operation.
   *
   * @param path the path
   * @return the load result
   */
  private static Icon load(String path)
  {
    return scaleIcon(IconLoader.getIcon(path, LauncherIcons.class), SERVICE_ICON_SIZE);
  }

  /**
   * Loads the custom.
   *
   * @param launcherConfiguration the launcher configuration
   * @return the load custom result
   */
  private static Icon loadCustom(ServiceLauncherSettings.LauncherConfiguration launcherConfiguration)
  {
    try
    {
      byte[] bytes = Base64.getDecoder().decode(launcherConfiguration.customIconData);
      Image image;
      if (launcherConfiguration.customIconName.toLowerCase().endsWith(".svg"))
      {
        image = loadSvg(bytes);
      }
      else
      {
        BufferedImage buffered = ImageIO.read(new ByteArrayInputStream(bytes));
        if (buffered == null)
        {
          return get(launcherConfiguration.icon);
        }
        image = buffered;
      }
      return new HighQualityImageIcon(image, SERVICE_ICON_SIZE);
    }
    catch (Exception ignored)
    {
      return get(launcherConfiguration.icon);
    }
  }

  /**
   * Loads the svg.
   *
   * @param bytes the bytes
   * @return the load svg result
   * @throws Exception if the operation cannot be completed
   */
  private static Image loadSvg(byte[] bytes) throws Exception
  {
    Class<?> svgLoader = Class.forName("com.intellij.util.SVGLoader");
    Method load = svgLoader.getMethod("load", InputStream.class, float.class);
    return (Image) load.invoke(null, new ByteArrayInputStream(bytes), SVG_RENDER_SCALE);
  }

  /**
   * Returns the result of scale icon.
   *
   * @param source the source
   * @param size the size
   * @return the scale icon result
   */
  static Icon scaleIcon(Icon source, int size)
  {
    int largestDimension = Math.max(1, Math.max(source.getIconWidth(), source.getIconHeight()));
    return IconUtil.scale(source, null, (float) size / largestDimension);
  }

}

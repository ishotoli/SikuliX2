/*
 * Copyright 2010-2014, Sikuli.org, Sikulix.com
 * Released under the MIT License.
 *
 * modified RaiMan
 */
package org.sikuli.script;

import java.awt.AWTException;
import java.awt.Rectangle;
import java.util.Date;

import com.sikulix.core.SX;
import com.sikulix.util.Settings;
import org.sikuli.util.Debug;
import org.sikuli.util.EventObserver;
import org.sikuli.util.EventSubject;
import org.sikuli.util.visual.OverlayCapturePrompt;
import org.sikuli.util.visual.ScreenHighlighter;

/**
 * A screen represents a physical monitor with its coordinates and size according to the global point system: the screen
 * areas are grouped around a point (0,0) like in a cartesian system (the top left corner and the points contained in
 * the screen area might have negative x and/or y values)
 * <br >The screens are arranged in an array (index = id) and each screen is always the same object (not possible to
 * create new objects).
 * <br>A screen inherits from class Region, so it can be used as such in all aspects. If you need the region of the
 * screen more than once, you have to create new ones based on the screen.
 * <br>The so called primary screen is the one with top left (0,0) and has id 0.
 */
public class Screen extends Region implements EventObserver, IScreen {

  static RunTime runTime = RunTime.getRunTime();

  private static String me = "Screen: ";
  private static int lvl = 3;
  private static Region fakeRegion;

  private static void log(int level, String message, Object... args) {
    Debug.logx(level, me + message, args);
  }

  private static IRobot globalRobot = null;
  protected static Screen[] screens = null;
  protected static int primaryScreen = -1;
  private static int waitForScreenshot = 300;
  protected IRobot robot = null;
  protected int curID = -1;
  protected int oldID = 0;
  protected int monitor = -1;
  protected boolean waitPrompt;
  protected OverlayCapturePrompt prompt;
  private final static String promptMsg = "Select a region on the screen";
  private ScreenImage lastScreenImage = null;

  //<editor-fold defaultstate="collapsed" desc="Initialization">
  private long lastCaptureTime = -1;

  static {
    initScreens(false);
  }

  public int getcurrentID() {
    return curID;
  }

  private static void initScreens(boolean reset) {
    if (screens != null && !reset) {
      return;
    }
    log(lvl + 1, "initScreens: entry");
    primaryScreen = 0;
    globalRobot = getMouseRobot();
    screens = new Screen[SX.getNumberOfMonitors()];
    screens[0] = new Screen(0, SX.getMainMonitorID());
    screens[0].initScreen();
    int nMonitor = 0;
    for (int i = 1; i < screens.length; i++) {
      if (nMonitor == SX.getMainMonitorID()) {
        nMonitor++;
      }
      screens[i] = new Screen(i, nMonitor);
      screens[i].initScreen();
      nMonitor++;
    }
    Mouse.init();
    if (getNumberScreens() > 1) {
      log(lvl, "initScreens: multi monitor mouse check");
      Location lnow = Mouse.at();
      float mmd = Settings.MoveMouseDelay;
      Settings.MoveMouseDelay = 0f;
      Location lc = null, lcn = null;
      for (Screen s : screens) {
        lc = s.getCenter();
        Mouse.move(lc);
        lcn = Mouse.at();
        if (!lc.equals(lcn)) {
          log(lvl, "*** multimonitor click check: %s center: (%d, %d) --- NOT OK:  (%d, %d)",
              s.toString(), lc.x, lc.y, lcn.x, lcn.y);
        } else {
          log(lvl, "*** checking: %s center: (%d, %d) --- OK", s.toString(), lc.x, lc.y);
        }
      }
      Mouse.move(lnow);
      Settings.MoveMouseDelay = mmd;
    }
  }

  protected static IRobot getMouseRobot() {
    try {
      if (globalRobot == null) {
        globalRobot = new RobotDesktop();
      }
    } catch (AWTException e) {
      Debug.error("Can't initialize global Robot for Mouse: " + e.getMessage());
      Commands.terminate(999);
    }
    return globalRobot;
  }

  protected static Region getFakeRegion() {
    if (fakeRegion == null) {
      fakeRegion = new Region(0, 0, 5, 5);
    }
    return fakeRegion;
  }

  /**
   * create a Screen (ScreenUnion) object as a united region of all available monitors
   *
   * @return ScreenUnion
   */
//TODO: check wether this can be a Screen object, to be tested: Region methods
  public static ScreenUnion all() {
    return new ScreenUnion();
  }

  // hack to get an additional internal constructor for the initialization
  private Screen(int id, boolean init) {
    super();
    curID = id;
  }

  // hack to get an additional internal constructor for the initialization
  private Screen(int id, int monitor) {
    super();
    curID = id;
    this.monitor = monitor;
  }

  public static Screen as(int id) {
    if (id < 0 || id >= SX.getNumberOfMonitors()) {
      Debug.error("Screen(%d) not in valid range 0 to %d - using primary %d",
          id, SX.getNumberOfMonitors() - 1, primaryScreen);
      return screens[0];
    } else {
      return screens[id];
    }
  }

  /**
   * The screen object with the given id
   *
   * @param id valid screen number
   */
  public Screen(int id) {
    super();
    if (id < 0 || id >= SX.getNumberOfMonitors()) {
      Debug.error("Screen(%d) not in valid range 0 to %d - using primary %d",
          id, SX.getNumberOfMonitors() - 1, primaryScreen);
      curID = primaryScreen;
    } else {
      curID = id;
    }
    monitor = screens[id].monitor;
    initScreen();
  }

  /**
   * INTERNAL USE collect all physical screens to one big region<br>
   * TODO: under evaluation, wether it really makes sense
   *
   * @param isScreenUnion true/false
   */
  public Screen(boolean isScreenUnion) {
    super(isScreenUnion);
  }

  /**
   * INTERNAL USE collect all physical screens to one big region<br>
   * This is under evaluation, wether it really makes sense
   */
  public void setAsScreenUnion() {
    oldID = curID;
    curID = -1;
  }

  /**
   * INTERNAL USE reset from being a screen union to the screen used before
   */
  public void setAsScreen() {
    curID = oldID;
  }

  /**
   * Is the screen object having the top left corner as (0,0). If such a screen does not exist it is the screen with id
   * 0.
   */
  public Screen() {
    super();
    curID = primaryScreen;
    initScreen();
  }

  /**
   * <br>TODO: remove this method if it is not needed
   *
   * @param scr
   */
  public void initScreen(Screen scr) {
    updateSelf();
  }

  private void initScreen() {
    Rectangle bounds = getBounds();
    x = (int) bounds.getX();
    y = (int) bounds.getY();
    w = (int) bounds.getWidth();
    h = (int) bounds.getHeight();
//    try {
//      robot = new RobotDesktop(this);
//      robot.setAutoDelay(10);
//    } catch (AWTException e) {
//      Debug.error("Can't initialize Java Robot on Screen " + curID + ": " + e.getMessage());
//      robot = null;
//    }
    robot = globalRobot;
  }

  /**
   * {@inheritDoc}
   *
   * @return Screen
   */
  @Override
  public Screen getScreen() {
    return this;
  }

  /**
   * Should not be used - throws UnsupportedOperationException
   *
   * @param s Screen
   * @return should not return
   */
  @Override
  protected Region setScreen(IScreen s) {
    throw new UnsupportedOperationException("The setScreen() method cannot be called from a Screen object.");
  }

  /**
   * show the current monitor setup
   */
  public static void showMonitors() {
//    initScreens();
    Debug.logp("*** monitor configuration [ %s Screen(s)] ***", Screen.getNumberScreens());
    Debug.logp("*** Primary is Screen %d", primaryScreen);
    for (int i = 0; i < SX.getNumberOfMonitors(); i++) {
      Debug.logp("Screen %d: %s", i, Screen.getScreen(i).toString());
    }
    Debug.logp("*** end monitor configuration ***");
  }

  /**
   * re-initialize the monitor setup (e.g. when it was changed while running)
   */
  public static void resetMonitors() {
    Debug.error("*** BE AWARE: experimental - might not work ***");
    Debug.error("Re-evaluation of the monitor setup has been requested");
    Debug.error("... Current Region/Screen objects might not be valid any longer");
    Debug.error("... Use existing Region/Screen objects only if you know what you are doing!");
    initScreens(true);
    Debug.logp("*** new monitor configuration [ %s Screen(s)] ***", Screen.getNumberScreens());
    Debug.logp("*** Primary is Screen %d", primaryScreen);
    for (int i = 0; i < SX.getNumberOfMonitors(); i++) {
      Debug.logp("Screen %d: %s", i, Screen.getScreen(i).toString());
    }
    Debug.error("*** end new monitor configuration ***");
  }

  //</editor-fold>
  //<editor-fold defaultstate="collapsed" desc="getters setters">
  protected boolean useFullscreen() {
    return false;
  }

  private static int getValidID(int id) {
    if (id < 0 || id >= SX.getNumberOfMonitors()) {
      Debug.error("Screen: invalid screen id %d - using primary screen", id);
      return primaryScreen;
    }
    return id;
  }

  private static int getValidMonitor(int id) {
    if (id < 0 || id >= SX.getNumberOfMonitors()) {
      Debug.error("Screen: invalid screen id %d - using primary screen", id);
      return SX.getMainMonitorID();
    }
    return screens[id].monitor;
  }

  /**
   *
   * @return number of available screens
   */
  public static int getNumberScreens() {
    return SX.getNumberOfMonitors();
  }

  /**
   *
   * @return the id of the screen at (0,0), if not exists 0
   */
  public static int getPrimaryId() {
    return primaryScreen;
  }

  /**
   *
   * @return the screen at (0,0), if not exists the one with id 0
   */
  public static Screen getPrimaryScreen() {
    return screens[primaryScreen];
  }

  /**
   *
   * @param id of the screen
   * @return the screen with given id, the primary screen if id is invalid
   */
  public static Screen getScreen(int id) {
    return screens[getValidID(id)];
  }

  /**
   *
   * @return the screen's rectangle
   */
  @Override
  public Rectangle getBounds() {
    return new Rectangle(SX.getMonitor(monitor));
  }

  /**
   *
   * @param id of the screen
   * @return the physical coordinate/size <br>as AWT.Rectangle to avoid mix up with getROI
   */
  public static Rectangle getBounds(int id) {
    return new Rectangle(SX.getMonitor(getValidMonitor(id)));
  }

  /**
   * each screen has exactly one robot (internally used for screen capturing)
   * <br>available as a convenience for those who know what they are doing. Should not be needed normally.
   *
   * @param id of the screen
   * @return the AWT.Robot of the given screen, if id invalid the primary screen
   */
  public static IRobot getRobot(int id) {
    return getScreen(id).getRobot();
  }

  /**
   *
   * @return the id
   */
  @Override
  public int getID() {
    return curID;
  }

  /**
   * INTERNAL USE: to be compatible with ScreenUnion
   *
   * @param x value
   * @param y value
   * @return id of the screen
   */
  @Override
  public int getIdFromPoint(int x, int y) {
    return curID;
  }

  /**
   * Gets the Robot of this Screen.
   *
   * @return The Robot for this Screen
   */
  @Override
  public IRobot getRobot() {
    return robot;
  }

  @Override
  public ScreenImage getLastScreenImageFromScreen() {
    return lastScreenImage;
  }

  //</editor-fold>
  //<editor-fold defaultstate="collapsed" desc="Capture - SelectRegion">
  /**
   * create a ScreenImage with the physical bounds of this screen
   *
   * @return the image
   */
  @Override
  public ScreenImage capture() {
    return capture(getRect());
  }

  /**
   * create a ScreenImage with given coordinates on this screen.
   *
   * @param x x-coordinate of the region to be captured
   * @param y y-coordinate of the region to be captured
   * @param w width of the region to be captured
   * @param h height of the region to be captured
   * @return the image of the region
   */
  @Override
  public ScreenImage capture(int x, int y, int w, int h) {
    Rectangle rect = new Rectangle(x, y, w, h);
    return capture(rect);
  }

  public ScreenImage captureforHighlight(int x, int y, int w, int h) {
    return robot.captureScreen(new Rectangle(x, y, w, h));
  }

  /**
   * create a ScreenImage with given rectangle on this screen.
   *
   * @param rect The Rectangle to be captured
   * @return the image of the region
   */
  @Override
  public ScreenImage capture(Rectangle rect) {
    lastCaptureTime = new Date().getTime();
    ScreenImage simg = robot.captureScreen(rect);
    lastScreenImage = simg;
    return simg;
  }
  
  public void setlastScreenImage(ScreenImage sImg) {
    lastScreenImage = sImg;
  }

  /**
   * create a ScreenImage with given region on this screen
   *
   * @param reg The Region to be captured
   * @return the image of the region
   */
  @Override
  public ScreenImage capture(Region reg) {
    return capture(reg.getRect());
  }

  /**
   * interactive capture with predefined message: lets the user capture a screen image using the mouse to draw the
   * rectangle
   *
   * @return the image
   */
  public ScreenImage userCapture() {
    return userCapture("");
  }

  public static void startPrompt(String message, EventObserver obs) {
    String msg = message.isEmpty() ? promptMsg : message;
    for (int is = 0; is < Screen.getNumberScreens(); is++) {
      Screen.getScreen(is).prompt = new OverlayCapturePrompt(Screen.getScreen(is), obs);
      Screen.getScreen(is).prompt.prompt(msg);
    }
  }

  public static void closePrompt() {
    for (int is = 0; is < Screen.getNumberScreens(); is++) {
      Screen.getScreen(is).prompt.close();
    }
  }

  /**
   * interactive capture with given message: lets the user capture a screen image using the mouse to draw the rectangle
   *
   * @param message text
   * @return the image
   */
  @Override
  public ScreenImage userCapture(final String message) {
    waitPrompt = true;
    Thread th = new Thread() {
      @Override
      public void run() {
        String msg = message.isEmpty() ? promptMsg : message;
        for (int is = 0; is < Screen.getNumberScreens(); is++) {
          Screen.getScreen(is).prompt = new OverlayCapturePrompt(Screen.getScreen(is), Screen.this);
          Screen.getScreen(is).prompt.prompt(msg);
        }
      }
    };
    th.start();
    boolean hasShot = true;
    try {
      int count = 0;
      while (waitPrompt) {
        Thread.sleep(100);
        if (count++ > waitForScreenshot) {
          hasShot = false;
          break;
        }
      }
    } catch (InterruptedException e) {
      hasShot = false;
    }
    ScreenImage simg = null;
    if (hasShot) {
      for (int is = 0; is < Screen.getNumberScreens(); is++) {
        if (simg == null) {
          simg = Screen.getScreen(is).prompt.getSelection();
          if (simg != null) {
            Screen.getScreen(is).lastScreenImage = simg;
          }
        }
        Screen.getScreen(is).prompt.close();
      }
    }
    return simg;
  }

  public String saveCapture(String name) {
    return saveCapture(name, null);
  }

  public String saveCapture(String name, Region reg) {
    ScreenImage img;
    if (reg == null) {
      img = userCapture("Capture for image " + name);
    } else {
      img = capture(reg);
    }
    if (img == null) {
      return null;
    } else {
      return img.saveInBundle(name);
    }
  }

  /**
   * interactive region create with predefined message: lets the user draw the rectangle using the mouse
   *
   * @return the region
   */
  public Region selectRegion() {
    return selectRegion("Select a region on the screen");
  }

  /**
   * interactive region create with given message: lets the user draw the rectangle using the mouse
   *
   * @param message text
   * @return the region
   */
  public Region selectRegion(final String message) {
    ScreenImage sim = userCapture(message);
    if (sim == null) {
      return null;
    }
    Rectangle r = sim.getROI();
    return Region.create((int) r.getX(), (int) r.getY(),
        (int) r.getWidth(), (int) r.getHeight());
  }

  /**
   * Internal use only
   *
   * @param s EventSubject
   */
  @Override
  public void update(EventSubject s) {
    waitPrompt = false;
  }
  //</editor-fold>

  //<editor-fold defaultstate="collapsed" desc="Visual effects">
  @Override
  public void showTarget(Location loc) {
    showTarget(loc, Settings.SlowMotionDelay);
  }

  protected void showTarget(Location loc, double secs) {
    if (Settings.ShowActions) {
      ScreenHighlighter overlay = new ScreenHighlighter(this, null);
      overlay.showTarget(loc, (float) secs);
    }
  }
  //</editor-fold>

  @Override
  public String toJSON() {
    Rectangle r = getBounds();
    return String.format("[\"R\", %d, %d, %d, %d]", r.x, r.y, r.width, r.height, curID);
  }
}

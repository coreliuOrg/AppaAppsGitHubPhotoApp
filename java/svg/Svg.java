//------------------------------------------------------------------------------
// Structured Vector Graphics
// Philip R Brenan at gmail dot com, Appa Apps Ltd, 2017
//------------------------------------------------------------------------------
// No need to draw the CompassRose when it is tiny as it probably blows things up.
package com.appaapps;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Point;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.RectF;
import java.io.ByteArrayOutputStream;
import java.util.Random;
import java.util.Stack;
import java.util.TreeMap;

public class Svg                                                                //C Structured Vector Graphics. Svg elements occupy fractions of the canvas which is assumed to have the approximate aspect ratio specified when creating the Svg. The elements then try to fit themselves into their fractional areas as best they can.
 {private static Svg lastShown = null;                                          // The last Svg shown
  private final static Themes themes = new Themes();                            // Colour themes generator
  private static Themes.Theme defaultTheme = themes.tartan();                   // Create a default theme for this element
  private final ColoursTransformed coloursTransformed =new ColoursTransformed();// Colour transformer
  private final Stack<Element> elements = new Stack<Element>();                 // Elements in the Svg
  private final Stack<Thread>  prepare  = new Stack<Thread>();                  // Threads preparing parts of the SVG and should be waited upon before the Svg is used
  private final static Random  random   = new Random();                         // Random number generator
  private final static float
    compassRoseGrowTime      = 5,                                               // The rate in pixels per second at which the compass rose grows
    preferBreaksToSpacesFactor = 1.2f,                                          // Indication of preference for breaking text at a space vs better scaling
    swipeFraction            = 1f/64f,                                          // Must move this fraction of the shortest dimension of the canvas to be regarded as a command swipe
    tapNotTouchTime          = 0.25f;                                           // A touch of less than this time in seconds is considered a tap
  private final static int
    overDragRelaxRate        =    8,                                            // The higher the slower the relaxation after an over drag but the less jitter at high magnification
    textSize                 =  128,                                            // The size of text used before scaling to fit the drawing area - this number needs to be large enough to produce clear characters but not so large that the hardware drawing layer does not complain
    textStrokeWidth          =    8,                                            // Stroke width used for drawing background of text
    textBackColour           = ColoursTransformed.black;                        // Text background colour
  private double glideTime   = 10;                                              // Default average number of seconds for an image to glide across its display area
  private int shown          = 0;                                               // Number of times shown after something else has been shown
  private boolean screenShotMode = false;                                       // Normally false, true if we are doing screen shots to make the glide slower and more stable
  private Double pressTime = null;                                              // Time latest touch started or null if the user is not touching the screen
  public double
    dragTimeTotal     = 0,                                                      // Time taken by drag in seconds
    dragTimeLast      = 0,                                                      // Time of last drag
    dt                = 0;                                                      // Time in seconds since touch if touch in progress
  public float
    X  = 0, Y         = 0,                                                      // Last touch press position in pixels if pressTime != null
    x  = 0, y         = 0,                                                      // Last touch drag position in pixels if pressTime != null
    dx = 0, dy        = 0;                                                      // Vector from previous drag  point to latest drag position
  private float
    distanceFromTouch = 0,                                                      // Distance in pixels from touch point
    dragFraction      = 0,                                                      // Fraction of drag disatnce over diagonal size of last canvas drawn
    lastCanvasWidth   = 0,                                                      // Width of the last canvas drawn on
    lastCanvasHeight  = 0;                                                      // Height of the last canvas drawn on
  private int backGroundColour = ColoursTransformed.black;                      // Background colour to be used for this svg

  public enum MenuMode                                                          // Menu presentation systems available
   {Rose, Page, None
   };

  private MenuMode menuMode = MenuMode.Rose;                                    // Default menu method
  private boolean pageMenuActive = false;                                       // Showing page menu representation of the compass rose
  private Svg svgPageMenu;                                                      // Svg to draw page menu

  final public CompassRose compassRose = new CompassRose();                     // Octoline for this Svg
  private Image.Mover imageMover = null;                                        // Image move request
  private Stack<Runnable> userTappedStack = new Stack<Runnable>();              // Allow taps within taps

  private Runnable
    userTapped = null,                                                          // Run when user taps
    userSelectedAnOctantWithNoCommand = null,                                   // Run when user selects an octant with no command
    userSelectedAnOctantThenCancelledOrMovedButNotEnough = null;                // Run when the user did none of the above

  private final static PointF[] oc = new PointF[8];                             // Attachment points for each rectangle containing the text of each compassRose command
  private final static Rect[]   or = new Rect  [8];                             // Rectangles containing the text for each compassRose command
  private final static Point[]  oj = new Point [8];                             // Justification of the text for each compassRose command
  private final float
    rh = (float)Math.sqrt(1d/2d),                                               // sqrt(1/2)
    ob = 2*(float)Math.sin(Math.PI/8),                                          // Octant base
    oi =   (float)Math.cos(Math.PI/8),                                          // Octant inner radius
    compassRoseRectUnits = 6;                                                   // Scale 'or' above by this amount to allow me to specify the rectangle positions as integers

  private Element
    pressedElement,                                                             // The graphics element last pressed
    releasedElement;                                                            // The graphics element last released

  public Svg()                                                                  //c Create Svg
   {or[0] = new Rect(-2,  0, +2, +4); oj[0] = new Point(0, -1); oc[0] = new PointF(0, -1);
    or[4] = new Rect(-2, -4, +2, +0); oj[4] = new Point(0, +1); oc[4] = new PointF(0, +1);

    or[2] = new Rect(+0, -2, +4, +2); oj[2] = new Point(-1, 0); oc[2] = new PointF(-1, 0);
    or[6] = new Rect(-4, -2, +0, +2); oj[6] = new Point(+1, 0); oc[6] = new PointF(+1, 0);

    or[1] = new Rect(-2, -2, +2, +2); oj[1] = new Point(0, 0);  oc[1] = new PointF(-rh, -rh);
    or[7] = new Rect(-2, -2, +2, +2); oj[7] = new Point(0, 0);  oc[7] = new PointF(+rh, -rh);

    or[3] = new Rect(-2, -2, +2, +2); oj[3] = new Point(0, 0);  oc[3] = new PointF(-rh, +rh);
    or[5] = new Rect(-2, -2, +2, +2); oj[5] = new Point(0, 0);  oc[5] = new PointF(+rh, +rh);
   }

  public void setBackGroundColour                                               //M Set the background colour
   (int backGroundColour)                                                       //P Back ground colour
   {this.backGroundColour = ColoursTransformed.darken(backGroundColour);        // Canvas.drawColour() ignores opacity
   }

  public void setMenuMode                                                       //M Set the menu mode
   (MenuMode menuMode)                                                          //P Menu mode
   {this.menuMode = menuMode;
   }

  public static MenuMode menuModeFromString                                     //M Get app menu mode from a string
   (final String mode,                                                          //P Mode as a string
    final MenuMode def)                                                         //P Default mode if no order specified
   {if      (mode == null)                  return def;
    else if (mode.equalsIgnoreCase("rose")) return MenuMode.Rose;
    else if (mode.equalsIgnoreCase("page")) return MenuMode.Page;
    return def;
   }

  public void setTheme                                                          //M Set the theme for all those elements in the Svg that use them - each element will get a different variant on the common theme provided by this set of patterns thus establishing a unifying theme with visual variation to provide interest for all these elements in the Svg
   (Themes.Theme theme)                                                         //P The theme to use
   {if (theme == null) theme = defaultTheme; else defaultTheme = theme;
    for(Element e: elements) e.setTheme(theme);
    setCompassRoseTheme(theme);                                                 // Set the current pattern for the Svg elements that use a pattern

   }

  public void setCompassRoseTheme                                               //M Set the set of themes for all those elements in the Svg that use them - each element will get a different variant on the common theme provided by this set of patterns thus establishing a unifying theme with visual variation to provide  interest for all these elements in the Svg
   (Themes.Theme theme)                                                         //P The theme to use
   {final CompassRose o = compassRose;
    if (o != null)
     {o.setTheme(theme);                                                        // Set theme of compass rose background
       for(CompassRose.Cmd c: o.commands)
       {if (c != null)
         {c.text.setTheme(theme);                                               // Set theme of compass rose text
         }
       }
     }
   }

  public void setScreenShotMode                                                 //M Set screen shot mode
   (boolean screenShotMode)                                                     //P Whether we are  in screen shot mode or not
   {this.screenShotMode = screenShotMode;
   }

  public void glideTime                                                         //M Set the current glide time for images
   (final double glideTime)                                                     //P Average glide time
   {this.glideTime = glideTime;
   }

  public void waitForPreparesToFinish()                                         //M Wait for any threads to finish that are preparing parts of the Svg
   {for(Thread t: prepare)
     {try{t.join();} catch(Exception e) {}
     }
   }

  public int shown()                                                            //M Give read access to number of times shown
   {return shown;
   }

  private void push                                                             //M Add an element to the stack of elements to be displayed
   (Element e)                                                                  //P the element to add
   {elements.push(e);
   }

  public void onShow()                                                          //M Called when this Svg is shown or reshown after some other Svg has been shown - alos called  by Activity.onResume() after the app has been restored
   {}

  public void userTapped                                                        //M Supply a thread whose run() method will be called on a separate thread when the user taps - single threads can only be used once but this double technique allows us to run the method as often as we wish
   (final Runnable runnable)                                                    //P Runnable to be run when the user taps
   {userTapped = runnable;
   }

  public void pushUserTapped                                                    //M Stack a user tapped action
   (final Runnable runnable)                                                    //P Runnable to be run when the user taps
   {userTappedStack.push(userTapped);                                           // Stack existing user tapped action
    userTapped = runnable;
   }

  public void popUserTapped()                                                   //M Unstack a user tapped action
   {if (userTappedStack.size() > 0) userTapped = userTappedStack.pop();         // Unstack the containing user tapped action if there is one
   }

  public void userSelectedAnOctantWithNoCommand                                 //M Called when an octant has been selected but there was no command supplied for that octant
   (final Runnable runnable)                                                    //P Runnable be run
   {userSelectedAnOctantWithNoCommand = runnable;
   }

  public void userSelectedAnOctantThenCancelledOrMovedButNotEnough              //M Called when an octant has been selected and then the user returned to the center and released to cancel the command selection or some movement was made but not enough to select anything
   (final Runnable runnable)                                                    //P Runnable be run
   {userSelectedAnOctantThenCancelledOrMovedButNotEnough = runnable;
   }

  public Svg draw                                                               //M Draw the Svg on the specified canvas
   (final Canvas canvas)                                                        //P Canvas to draw on
   {final Double pressTimeF = pressTime;
     if (pressTimeF != null) dt = (float)(Time.secs() - pressTimeF);            // Update time in seconds since press if  touch in progress
    lastCanvasWidth  = canvas.getWidth();                                       // Save canvas dimensions for use in motion processing
    lastCanvasHeight = canvas.getHeight();
    canvas.drawColor(backGroundColour);                                         // Fill the canvas with the back ground colour

    if (lastShown != this && (svgPageMenu == null || lastShown != svgPageMenu)) // A new show?
     {onShow();
      lastShown = this;
      ++shown;                                                                  // Number of times shown-a-new
     }

    if (imageMover == null)                                                     // Allow animations if no image mover is present
     {for(Element e: elements)                                                  // Draw elements that are both animated and active on top of other elements
       {if (e.centerToCenter == null || e.centerToCenter.active() == 0)         // No animation or animation not active
         {if (e.visible) e.drawElement(canvas);                                 // Draw visible elements
         }
       }
      for(Element e: elements)                                                  // Animated and active
       {if (e.centerToCenter != null && e.centerToCenter.active() != 0)
         {if (e.visible) e.drawElement(canvas);                                 // Draw visible elements
         }
       }
     }
    else                                                                        // No animations if image mover is present
     {for(Element e: elements)                                                  // Animated and active
       {if (e.visible) e.drawElement(canvas);                                   // Draw visible elements
       }
     }

    if (pressTime != null)                                                      // Show Octoline menu during long presses
     {if (compassRose.numberOfActiveCompassRoseCommands > 0)                    // Show Octoline menu during long presses if the menu mode is Rose
       {if (menuMode == MenuMode.Rose)                                          // Show Octoline menu during long presses if the menu mode is Rose and there are menu comamnds
         {compassRose.drawElement(canvas);                                      // Draw the rose representation of the compassRose
         }
        else if (menuMode == MenuMode.Page)                                     // Show the compass rose via a page menu
         {if (Time.secs() - pressTime > tapNotTouchTime)                        // Pressed long enough not to be a tap
           {if (!pageMenuActive)                                                // The page menu is not currently being displayed
             {if (svgPageMenu == null)                                          // Create the page menu if necessary
               {svgPageMenu = compassRose.createPageMenu();
               }
              pageMenuActive = true;                                            // Show the page menu
             }
           }
         }
       }
     }

    if (pageMenuActive)                                                         // Draw the page menu
     {final Svg s = svgPageMenu;
      if (s != null)
       {s.draw(canvas);
        for(Element e: s.elements)                                              // Highlight the active square
         {if (e instanceof Text)
           {final Text t = (Text)e;
            t.setBlockColour(t.drawArea.contains(x, y) ? 0xffffffff : 0);       //I Set block color to white to show the selected option
           }
         }
       }
     }

    return this;
   }

  private void runTapAction                                                     //M Run the tap action for this svg at the specified point
   (final float x,                                                              //P X coordinate of tap
    final float y)                                                              //P Y coordinate of tap
   {for(Element e: elements)                                                    // Find the active element
     {final Runnable r = e.actionTap;                                           // Finalize tap action
      if (r != null)
       {if (e.drawArea.contains(x, y))                                          // Locate tap point
         {startThread(r);                                                       // Run action
         }
       }
     }
   }

  private void runPageMenuTapAction                                             //M Run the page menu tap action for this svg at the specified point
   (final float x,                                                              //P X coordinate of tap
    final float y)                                                              //P Y coordinate of tap
   {final Svg s = svgPageMenu;                                                  // Finalize page menu
    if (s != null) s.runTapAction(x, y);                                        // Run page menu action
   }

  abstract public class Element                                                 //C Common features of a drawn element
   {protected final RectF
      target  = new RectF(),                                                    // Area of canvas occupied by this element in fractional units relative to canvas
      targetH = new RectF(),                                                    // Area of canvas occupied by this element in fractional units relative to canvas - horizontal layout
      targetV = new RectF();                                                    // Area of canvas occupied by this element in fractional units relative to canvas - vertical layout
    protected final RectF drawArea = new RectF();                               // Area of canvas occupied by this element in pixels
    private CenterToCenter centerToCenter;                                      // Animation if required
    private boolean visible = true;                                             // Visibility of element
    private String name = null;                                                 // The name of the element
    private Runnable actionTap = null;                                          // Action if user taps on this element

    private Element                                                             //c Create en element by specifying its fractional area on the canvas
     (final float x,                                                            //P Fractional area in which to display the element - horizontal - left
      final float y,                                                            //P Fractional area in which to display the element - horizontal - upper
      final float 𝘅,                                                            //P Fractional area in which to display the element - horizontal - right
      final float 𝘆,                                                            //P Fractional area in which to display the element - horizontal - lower
      final float X,                                                            //P Fractional area in which to display the element - vertical - left
      final float Y,                                                            //P Fractional area in which to display the element - vertical - upper
      final float 𝗫,                                                            //P Fractional area in which to display the element - vertical - right
      final float 𝗬)                                                            //P Fractional area in which to display the element - vertical - lower
     {targetH.set(x, y, 𝘅, 𝘆);                                                   // Horizontal layout
      targetV.set(X, Y, 𝗫, 𝗬);                                                  // Vertical layout
     }

    protected void drawElement                                                  //M Draw the SVG on a canvas
     (final Canvas canvas)                                                      //P Canvas to draw on
     {final Svg s = Svg.this;
      final float w = canvas.getWidth(), h = canvas.getHeight();                // Canvas dimensions
      target.set(w > h ? targetH : targetV);                                    // Horizontal or vertical layout

      if (centerToCenter != null && imageMover == null)                         // Apply animation if present and we are not manually moving an image
       {centerToCenter.scaleDrawArea(canvas);
       }
      else                                                                      // Otherwise set normal draw area
       {drawArea.set(target.left  * w, target.top    * h,                       // Fix drawing area
                     target.right * w, target.bottom * h);
       }

      drawElementOnCanvas(canvas);                                              // Draw the element in the  drawing area
     }

    public RectF drawArea()                                                     //M Actual area on display where the element to is be drawn in pixels
     {return drawArea;
     }

    protected void drawElementOnCanvas                                          //M Over-ridden by each element to draw itself on the canvas provided
     (final Canvas canvas)                                                      //P Canvas on which the element should be drawn
     {}

    protected boolean contains                                                  //M Does this element contain the point (x, y)?
     (float x,                                                                  //P Point.X
      float y)                                                                  //P Point.Y
     {final Svg s = Svg.this;
      return drawArea.contains(x, y);
     }

    public void setName                                                         //M Set the name of this element
     (String name)                                                              //P The name of this element
     {this.name = name;
     }

    public String getName()                                                     //M Get the name of this element
     {return name;
     }

    public void tapAction                                                       //M Set the action to be performed if the user taps on this element
     (Runnable actionTap)                                                       //P Run this runnable if the user taps on this element
     {this.actionTap = actionTap;
     }

    public void setTheme                                                        //M Override to set the theme for this element if it uses one
     (Themes.Theme theme)                                                       //P The theme to use
     {}

    public void setVisible                                                      //M Set visibility for this element
     (final boolean visible)                                                    //P true - make this element visible, false - make it invisible
     {this.visible = visible;
     }

    private CenterToCenter setCenterToCenter                                    //M Create and set Center To Center animation for this element
     (final double delay,                                                       //P Delay before animation starts
      final double duration,                                                    //P Duration of expansion and contraction
      final double startAgain,                                                  //P Restart again after this time
      final float  x,                                                           //P Fractional position to expand to - left
      final float  y,                                                           //P Fractional position to expand to - upper
      final float  𝘅,                                                           //P Fractional position to expand to - right
      final float  𝘆)                                                           //P Fractional position to expand to - lower
     {return centerToCenter = new CenterToCenter
       (delay, duration, startAgain, x, y, 𝘅, 𝘆);
     }

    protected CenterToCenter setCenterToCenter                                  //M Set Center To Center animation for this element
     (final CenterToCenter animation)                                           //P Animation
     {return centerToCenter = animation;
     }

    protected void clearCenterToCenter()                                        //M Clear animation for this element
     {centerToCenter = null;
     }

    public String type()                                                        //M Type of this element
     {return "Svg.Element";
     }

    protected class CenterToCenter                                              //C Animation which expands an element its center is over the centre of the canvas
     {private final double delay, duration, startAgain, startTime;              // Initial delay, duration of animation, restart delay, time animation started all in seconds
      private final RectF expanse = new RectF();                                // The target area for the expanded animation
      private boolean wasActive = false;                                        // Animation was active when last examined

      protected CenterToCenter                                                  //c Create a center to center animation
       (final double delay,                                                     //P Delay before animation starts in seconds
        final double duration,                                                  //P Duration of animation in seconds
        final double startAgain,                                                //P Restart after animation after this time
        final float  x,                                                         //P Fractional position to expand to - left
        final float  y,                                                         //P Fractional position to expand to - upper
        final float  𝘅,                                                         //P Fractional position to expand to - right
        final float  𝘆)                                                         //P Fractional position to expand to - lower
       {this.delay      = delay;
        this.duration   = duration;
        this.startAgain = startAgain;
        this.startTime  = Time.secs();
        expanse.set(x, y, 𝘅, 𝘆);
       }

      protected CenterToCenter                                                  //c Clone a center to center animation
       (final CenterToCenter clone)                                             //P Animation to clone
       {this.delay      = clone.delay;
        this.duration   = clone.duration;
        this.startAgain = clone.startAgain;
        this.startTime  = Time.secs();
        expanse.set(clone.expanse);
       }

      public CenterToCenter setExpanse                                          //M Modify the expanse: the expanse is the fully expanded area of the canvas expressed in fractional units that the animation seeks to occupy
       (final float  x,                                                         //P Fractional position to expand to - left
        final float  y,                                                         //P Fractional position to expand to - upper
        final float  𝘅,                                                         //P Fractional position to expand to - right
        final float  𝘆)                                                         //P Fractional position to expand to - lower
       {expanse.set(x, y, 𝘅, 𝘆);                                                // Set expanse
        return this;                                                            // Assist chaining
       }

      protected float active()                                                  //M Get fraction of animation
       {final double
         t = Time.secs(),                                                       // Time
         a = delay,                                                             // Initial delay
         d = duration,                                                          // Duration
         p = a + d + startAgain,                                                // Period
         r = (t - startTime) % p,                                               // Position in repetition
         f = (r - a) / d;                                                       // Fraction

        if (f >= 0 && f <= 1)                                                   // Active period
         {if (!wasActive)                                                       // Call the activate method when the animation is activated
           {onActivate();
            wasActive = true;
           }
          final double s = Math.sin(f * Math.PI), S = s * s;                    // Sine, sine squared
          return (float)S;                                                      // Active fraction as sine squared Float
         }
        else if (wasActive)                                                     // Call onFinished() if we have just finished an animation
         {onFinished();
         }

        wasActive = false;
        return 0;
       }

      protected void scaleDrawArea                                              //M Apply scale to the drawing area
       (Canvas canvas)                                                          //P Canvas to which scaling is to be applied
       {final float s = active();
        final float w = canvas.getWidth(), h = canvas.getHeight();

        drawArea.set                                                            // Actual drawing area
         (((expanse.left   - target.left  ) * s + target.left  ) * w,
          ((expanse.top    - target.top   ) * s + target.top   ) * h,
          ((expanse.right  - target.right ) * s + target.right ) * w,
          ((expanse.bottom - target.bottom) * s + target.bottom) * h);
       }

      protected void onActivate()                                               //M Called when this animation becomes active
       {}
      protected void onFinished()                                               //M Called when this animation completes
       {}
     } //C CenterToCenter
   } //C Element

  abstract private class Element2                                               //C Common features of an element drawn with two paints - this is in effect a replacement for ComposeShader which does not seem to work - it always produces a white screen
    extends Element                                                             //E Element
   {protected Themes.Theme theme = defaultTheme.make();                         // Use a variant on the default theme to draw this element os that it can be seen - the caller can always set a different set of patterns via Svg.setTheme()

    private Element2                                                            //c Create en element by specifying its fractional area on the canvas
     (final float x,                                                            //P Fractional area in which to display the element - horizontal - left
      final float y,                                                            //P Fractional area in which to display the element - horizontal - upper
      final float 𝘅,                                                            //P Fractional area in which to display the element - horizontal - right
      final float 𝘆,                                                            //P Fractional area in which to display the element - horizontal - lower
      final float X,                                                            //P Fractional area in which to display the element - vertical - left
      final float Y,                                                            //P Fractional area in which to display the element - vertical - upper
      final float 𝗫,                                                            //P Fractional area in which to display the element - vertical - right
      final float 𝗬)                                                            //P Fractional area in which to display the element - vertical - lower
     {super(x, y, 𝘅, 𝘆, X, Y, 𝗫, 𝗬);
     }

    public void setTheme                                                        //M Set the theme for this element
     (final Themes.Theme theme)                                                 //P The theme to use
     {this.theme = theme.make();
     }

    public String getThemeName()                                                //M Get the theme name used to draw this element
     {return theme.name();
     }

    protected void drawElement                                                  //M Draw the Svg on the canvas
     (final Canvas canvas)                                                      //P Canvas to draw on
     {theme.set(canvas, width(canvas), height(canvas));                         // Set the themes
      super.drawElement(canvas);
     }

    protected float width                                                       //M Approximate width of object before scaling
     (final Canvas canvas)                                                      //P Canvas to draw on
     {return canvas.getWidth()  * target.width();
     }

    protected float height                                                      //M Approximate height of object before scaling
     (final Canvas canvas)                                                      //P Canvas to draw on
     {return canvas.getHeight() * target.height();
     }
   } //C Element2

  protected class Rectangle                                                     //C Draw a rectangle
    extends Element2                                                            //E So we can use a theme to paint the rectangle
   {private Rectangle                                                           //c Create a rectangle
      (final float x,                                                           //P Fractional position - horizontal - left
       final float y,                                                           //P Fractional position - horizontal - upper
       final float 𝘅,                                                           //P Fractional position - horizontal - right
       final float 𝘆,                                                           //P Fractional position - horizontal - lower
       final float X,                                                           //P Fractional position - vertical - left
       final float Y,                                                           //P Fractional position - vertical - upper
       final float 𝗫,                                                           //P Fractional position - vertical - right
       final float 𝗬)                                                           //P Fractional position - vertical - lower
     {super(x, y, 𝘅, 𝘆, X, Y, 𝗫, 𝗬);                                            // Create the element containing the drawing of the rectangle
     }

    protected void drawElementOnCanvas                                          //O=com.appaapps.Svg.Element2.drawElementOnCanvas Draw the rectangle
     (final Canvas canvas)                                                      //P Canvas to draw on
     {canvas.drawRect(drawArea, theme.p);                                       // Draw rectangle with first paint
      canvas.drawRect(drawArea, theme.q);                                       // Draw rectangle with second paint
     }
   } //C Rectangle

  public Rectangle Rectangle                                                    //M Create a rectangle regardless of orientation
   (final float x,                                                              //P Fractional position - left
    final float y,                                                              //P Fractional position - upper
    final float 𝘅,                                                              //P Fractional position - right
    final float 𝘆)                                                              //P Fractional position - lower
   {final Rectangle r = new Rectangle(x, y, 𝘅, 𝘆, x, y, 𝘅, 𝘆);                  // Create the new rectangle
    push(r);                                                                    // Push it on the stack of elements
    return r;
   }

  public Rectangle Rectangle                                                    //M Create a rectangle with regard to orientation
   (final float x,                                                              //P Fractional position - horizontal - left
    final float y,                                                              //P Fractional position - horizontal - upper
    final float 𝘅,                                                              //P Fractional position - horizontal - right
    final float 𝘆,                                                              //P Fractional position - horizontal - lower
    final float X,                                                              //P Fractional position - vertical - left
    final float Y,                                                              //P Fractional position - vertical - upper
    final float 𝗫,                                                              //P Fractional position - vertical - right
    final float 𝗬)                                                              //P Fractional position - vertical - lower
   {final Rectangle r = new Rectangle(x, y, 𝘅, 𝘆, X, Y, 𝗫, 𝗬);                  // Create the new rectangle
    push(r);                                                                    // Push it on the stack of elements
    return r;
   }

  public class Text                                                             //C Draw some text
    extends Element2                                                            //E So we can draw the text with a theme
   {final protected String text;                                                // The text to display
    final private int justifyX, justifyY, justify𝗫, justify𝗬;                   // Justification in x and y for each orientation
    final private RectF
      textArea  = new RectF(),                                                  // Preallocated rectangle for text bounds
      textDraw  = new RectF(),                                                  // Preallocated rectangle for enclosing the current line of text
      textUnion = new RectF();                                                  // Preallocated rectangle for smallest rectangle enclosing all the text
    final private Path textPath = new Path();                                   // Preallocated path for text
    final private Paint
      paint  = new Paint(),                                                     // Paint to calculate the text path
      back   = new Paint(),                                                     // Paint outline background of text - this is drawn as a thin black line around each character to increase the contrast of each character
      block  = new Paint();                                                     // Paint for a rectangle behind the text to increase the contrast of the text
    final private float
      widthOfString;                                                            // The length of the string when drawn on one line
    final private int
      numberOfChars;                                                            // Length of text string
    final private Stack<Layout>
       layouts = new Stack<Layout>();                                           // The possible layouts
    final int[][][]breakingLayoutTable = { {{1}}, {{2}, {1, 1}}, {{3}, {2, 1}, {1, 2}, {1, 1, 1}}, { {4}, {3, 1}, {2, 2}, {2, 1, 1}, {1, 3}, {1, 2, 1}, {1, 1, 2}, {1, 1, 1, 1}, }, { {5}, {4, 1}, {3, 2}, {3, 1, 1}, {2, 3}, {2, 2, 1}, {2, 1, 2}, {2, 1, 1, 1}, {1, 4}, {1, 3, 1}, {1, 2, 2}, {1, 2, 1, 1}, {1, 1, 3}, {1, 1, 2, 1}, {1, 1, 1, 2}, {1, 1, 1, 1, 1}, }, { {6}, {5, 1}, {4, 2}, {4, 1, 1}, {3, 3}, {3, 2, 1}, {3, 1, 2}, {3, 1, 1, 1}, {2, 4}, {2, 3, 1}, {2, 2, 2}, {2, 2, 1, 1}, {2, 1, 3}, {2, 1, 2, 1}, {2, 1, 1, 2}, {2, 1, 1, 1, 1}, {1, 5}, {1, 4, 1}, {1, 3, 2}, {1, 3, 1, 1}, {1, 2, 3}, {1, 2, 2, 1}, {1, 2, 1, 2}, {1, 2, 1, 1, 1}, {1, 1, 4}, {1, 1, 3, 1}, {1, 1, 2, 2}, {1, 1, 2, 1, 1}, {1, 1, 1, 3}, {1, 1, 1, 2, 1}, {1, 1, 1, 1, 2}, {1, 1, 1, 1, 1, 1}, }, { {7}, {6, 1}, {5, 2}, {5, 1, 1}, {4, 3}, {4, 2, 1}, {4, 1, 2}, {4, 1, 1, 1}, {3, 4}, {3, 3, 1}, {3, 2, 2}, {3, 2, 1, 1}, {3, 1, 3}, {3, 1, 2, 1}, {3, 1, 1, 2}, {3, 1, 1, 1, 1}, {2, 5}, {2, 4, 1}, {2, 3, 2}, {2, 3, 1, 1}, {2, 2, 3}, {2, 2, 2, 1}, {2, 2, 1, 2}, {2, 2, 1, 1, 1}, {2, 1, 4}, {2, 1, 3, 1}, {2, 1, 2, 2}, {2, 1, 2, 1, 1}, {2, 1, 1, 3}, {2, 1, 1, 2, 1}, {2, 1, 1, 1, 2}, {2, 1, 1, 1, 1, 1}, {1, 6}, {1, 5, 1}, {1, 4, 2}, {1, 4, 1, 1}, {1, 3, 3}, {1, 3, 2, 1}, {1, 3, 1, 2}, {1, 3, 1, 1, 1}, {1, 2, 4}, {1, 2, 3, 1}, {1, 2, 2, 2}, {1, 2, 2, 1, 1}, {1, 2, 1, 3}, {1, 2, 1, 2, 1}, {1, 2, 1, 1, 2}, {1, 2, 1, 1, 1, 1}, {1, 1, 5}, {1, 1, 4, 1}, {1, 1, 3, 2}, {1, 1, 3, 1, 1}, {1, 1, 2, 3}, {1, 1, 2, 2, 1}, {1, 1, 2, 1, 2}, {1, 1, 2, 1, 1, 1}, {1, 1, 1, 4}, {1, 1, 1, 3, 1}, {1, 1, 1, 2, 2}, {1, 1, 1, 2, 1, 1}, {1, 1, 1, 1, 3}, {1, 1, 1, 1, 2, 1}, {1, 1, 1, 1, 1, 2}, {1, 1, 1, 1, 1, 1, 1}, }, { {8}, {7, 1}, {6, 2}, {6, 1, 1}, {5, 3}, {5, 2, 1}, {5, 1, 2}, {5, 1, 1, 1}, {4, 4}, {4, 3, 1}, {4, 2, 2}, {4, 2, 1, 1}, {4, 1, 3}, {4, 1, 2, 1}, {4, 1, 1, 2}, {4, 1, 1, 1, 1}, {3, 5}, {3, 4, 1}, {3, 3, 2}, {3, 3, 1, 1}, {3, 2, 3}, {3, 2, 2, 1}, {3, 2, 1, 2}, {3, 2, 1, 1, 1}, {3, 1, 4}, {3, 1, 3, 1}, {3, 1, 2, 2}, {3, 1, 2, 1, 1}, {3, 1, 1, 3}, {3, 1, 1, 2, 1}, {3, 1, 1, 1, 2}, {3, 1, 1, 1, 1, 1}, {2, 6}, {2, 5, 1}, {2, 4, 2}, {2, 4, 1, 1}, {2, 3, 3}, {2, 3, 2, 1}, {2, 3, 1, 2}, {2, 3, 1, 1, 1}, {2, 2, 4}, {2, 2, 3, 1}, {2, 2, 2, 2}, {2, 2, 2, 1, 1}, {2, 2, 1, 3}, {2, 2, 1, 2, 1}, {2, 2, 1, 1, 2}, {2, 2, 1, 1, 1, 1}, {2, 1, 5}, {2, 1, 4, 1}, {2, 1, 3, 2}, {2, 1, 3, 1, 1}, {2, 1, 2, 3}, {2, 1, 2, 2, 1}, {2, 1, 2, 1, 2}, {2, 1, 2, 1, 1, 1}, {2, 1, 1, 4}, {2, 1, 1, 3, 1}, {2, 1, 1, 2, 2}, {2, 1, 1, 2, 1, 1}, {2, 1, 1, 1, 3}, {2, 1, 1, 1, 2, 1}, {2, 1, 1, 1, 1, 2}, {2, 1, 1, 1, 1, 1, 1}, {1, 7}, {1, 6, 1}, {1, 5, 2}, {1, 5, 1, 1}, {1, 4, 3}, {1, 4, 2, 1}, {1, 4, 1, 2}, {1, 4, 1, 1, 1}, {1, 3, 4}, {1, 3, 3, 1}, {1, 3, 2, 2}, {1, 3, 2, 1, 1}, {1, 3, 1, 3}, {1, 3, 1, 2, 1}, {1, 3, 1, 1, 2}, {1, 3, 1, 1, 1, 1}, {1, 2, 5}, {1, 2, 4, 1}, {1, 2, 3, 2}, {1, 2, 3, 1, 1}, {1, 2, 2, 3}, {1, 2, 2, 2, 1}, {1, 2, 2, 1, 2}, {1, 2, 2, 1, 1, 1}, {1, 2, 1, 4}, {1, 2, 1, 3, 1}, {1, 2, 1, 2, 2}, {1, 2, 1, 2, 1, 1}, {1, 2, 1, 1, 3}, {1, 2, 1, 1, 2, 1}, {1, 2, 1, 1, 1, 2}, {1, 2, 1, 1, 1, 1, 1}, {1, 1, 6}, {1, 1, 5, 1}, {1, 1, 4, 2}, {1, 1, 4, 1, 1}, {1, 1, 3, 3}, {1, 1, 3, 2, 1}, {1, 1, 3, 1, 2}, {1, 1, 3, 1, 1, 1}, {1, 1, 2, 4}, {1, 1, 2, 3, 1}, {1, 1, 2, 2, 2}, {1, 1, 2, 2, 1, 1}, {1, 1, 2, 1, 3}, {1, 1, 2, 1, 2, 1}, {1, 1, 2, 1, 1, 2}, {1, 1, 2, 1, 1, 1, 1}, {1, 1, 1, 5}, {1, 1, 1, 4, 1}, {1, 1, 1, 3, 2}, {1, 1, 1, 3, 1, 1}, {1, 1, 1, 2, 3}, {1, 1, 1, 2, 2, 1}, {1, 1, 1, 2, 1, 2}, {1, 1, 1, 2, 1, 1, 1}, {1, 1, 1, 1, 4}, {1, 1, 1, 1, 3, 1}, {1, 1, 1, 1, 2, 2}, {1, 1, 1, 1, 2, 1, 1}, {1, 1, 1, 1, 1, 3}, {1, 1, 1, 1, 1, 2, 1}, {1, 1, 1, 1, 1, 1, 2}, {1, 1, 1, 1, 1, 1, 1, 1}, }, { {9}, {8, 1}, {7, 2}, {7, 1, 1}, {6, 3}, {6, 2, 1}, {6, 1, 2}, {6, 1, 1, 1}, {5, 4}, {5, 3, 1}, {5, 2, 2}, {5, 2, 1, 1}, {5, 1, 3}, {5, 1, 2, 1}, {5, 1, 1, 2}, {5, 1, 1, 1, 1}, {4, 5}, {4, 4, 1}, {4, 3, 2}, {4, 3, 1, 1}, {4, 2, 3}, {4, 2, 2, 1}, {4, 2, 1, 2}, {4, 2, 1, 1, 1}, {4, 1, 4}, {4, 1, 3, 1}, {4, 1, 2, 2}, {4, 1, 2, 1, 1}, {4, 1, 1, 3}, {4, 1, 1, 2, 1}, {4, 1, 1, 1, 2}, {4, 1, 1, 1, 1, 1}, {3, 6}, {3, 5, 1}, {3, 4, 2}, {3, 4, 1, 1}, {3, 3, 3}, {3, 3, 2, 1}, {3, 3, 1, 2}, {3, 3, 1, 1, 1}, {3, 2, 4}, {3, 2, 3, 1}, {3, 2, 2, 2}, {3, 2, 2, 1, 1}, {3, 2, 1, 3}, {3, 2, 1, 2, 1}, {3, 2, 1, 1, 2}, {3, 2, 1, 1, 1, 1}, {3, 1, 5}, {3, 1, 4, 1}, {3, 1, 3, 2}, {3, 1, 3, 1, 1}, {3, 1, 2, 3}, {3, 1, 2, 2, 1}, {3, 1, 2, 1, 2}, {3, 1, 2, 1, 1, 1}, {3, 1, 1, 4}, {3, 1, 1, 3, 1}, {3, 1, 1, 2, 2}, {3, 1, 1, 2, 1, 1}, {3, 1, 1, 1, 3}, {3, 1, 1, 1, 2, 1}, {3, 1, 1, 1, 1, 2}, {3, 1, 1, 1, 1, 1, 1}, {2, 7}, {2, 6, 1}, {2, 5, 2}, {2, 5, 1, 1}, {2, 4, 3}, {2, 4, 2, 1}, {2, 4, 1, 2}, {2, 4, 1, 1, 1}, {2, 3, 4}, {2, 3, 3, 1}, {2, 3, 2, 2}, {2, 3, 2, 1, 1}, {2, 3, 1, 3}, {2, 3, 1, 2, 1}, {2, 3, 1, 1, 2}, {2, 3, 1, 1, 1, 1}, {2, 2, 5}, {2, 2, 4, 1}, {2, 2, 3, 2}, {2, 2, 3, 1, 1}, {2, 2, 2, 3}, {2, 2, 2, 2, 1}, {2, 2, 2, 1, 2}, {2, 2, 2, 1, 1, 1}, {2, 2, 1, 4}, {2, 2, 1, 3, 1}, {2, 2, 1, 2, 2}, {2, 2, 1, 2, 1, 1}, {2, 2, 1, 1, 3}, {2, 2, 1, 1, 2, 1}, {2, 2, 1, 1, 1, 2}, {2, 2, 1, 1, 1, 1, 1}, {2, 1, 6}, {2, 1, 5, 1}, {2, 1, 4, 2}, {2, 1, 4, 1, 1}, {2, 1, 3, 3}, {2, 1, 3, 2, 1}, {2, 1, 3, 1, 2}, {2, 1, 3, 1, 1, 1}, {2, 1, 2, 4}, {2, 1, 2, 3, 1}, {2, 1, 2, 2, 2}, {2, 1, 2, 2, 1, 1}, {2, 1, 2, 1, 3}, {2, 1, 2, 1, 2, 1}, {2, 1, 2, 1, 1, 2}, {2, 1, 2, 1, 1, 1, 1}, {2, 1, 1, 5}, {2, 1, 1, 4, 1}, {2, 1, 1, 3, 2}, {2, 1, 1, 3, 1, 1}, {2, 1, 1, 2, 3}, {2, 1, 1, 2, 2, 1}, {2, 1, 1, 2, 1, 2}, {2, 1, 1, 2, 1, 1, 1}, {2, 1, 1, 1, 4}, {2, 1, 1, 1, 3, 1}, {2, 1, 1, 1, 2, 2}, {2, 1, 1, 1, 2, 1, 1}, {2, 1, 1, 1, 1, 3}, {2, 1, 1, 1, 1, 2, 1}, {2, 1, 1, 1, 1, 1, 2}, {2, 1, 1, 1, 1, 1, 1, 1}, {1, 8}, {1, 7, 1}, {1, 6, 2}, {1, 6, 1, 1}, {1, 5, 3}, {1, 5, 2, 1}, {1, 5, 1, 2}, {1, 5, 1, 1, 1}, {1, 4, 4}, {1, 4, 3, 1}, {1, 4, 2, 2}, {1, 4, 2, 1, 1}, {1, 4, 1, 3}, {1, 4, 1, 2, 1}, {1, 4, 1, 1, 2}, {1, 4, 1, 1, 1, 1}, {1, 3, 5}, {1, 3, 4, 1}, {1, 3, 3, 2}, {1, 3, 3, 1, 1}, {1, 3, 2, 3}, {1, 3, 2, 2, 1}, {1, 3, 2, 1, 2}, {1, 3, 2, 1, 1, 1}, {1, 3, 1, 4}, {1, 3, 1, 3, 1}, {1, 3, 1, 2, 2}, {1, 3, 1, 2, 1, 1}, {1, 3, 1, 1, 3}, {1, 3, 1, 1, 2, 1}, {1, 3, 1, 1, 1, 2}, {1, 3, 1, 1, 1, 1, 1}, {1, 2, 6}, {1, 2, 5, 1}, {1, 2, 4, 2}, {1, 2, 4, 1, 1}, {1, 2, 3, 3}, {1, 2, 3, 2, 1}, {1, 2, 3, 1, 2}, {1, 2, 3, 1, 1, 1}, {1, 2, 2, 4}, {1, 2, 2, 3, 1}, {1, 2, 2, 2, 2}, {1, 2, 2, 2, 1, 1}, {1, 2, 2, 1, 3}, {1, 2, 2, 1, 2, 1}, {1, 2, 2, 1, 1, 2}, {1, 2, 2, 1, 1, 1, 1}, {1, 2, 1, 5}, {1, 2, 1, 4, 1}, {1, 2, 1, 3, 2}, {1, 2, 1, 3, 1, 1}, {1, 2, 1, 2, 3}, {1, 2, 1, 2, 2, 1}, {1, 2, 1, 2, 1, 2}, {1, 2, 1, 2, 1, 1, 1}, {1, 2, 1, 1, 4}, {1, 2, 1, 1, 3, 1}, {1, 2, 1, 1, 2, 2}, {1, 2, 1, 1, 2, 1, 1}, {1, 2, 1, 1, 1, 3}, {1, 2, 1, 1, 1, 2, 1}, {1, 2, 1, 1, 1, 1, 2}, {1, 2, 1, 1, 1, 1, 1, 1}, {1, 1, 7}, {1, 1, 6, 1}, {1, 1, 5, 2}, {1, 1, 5, 1, 1}, {1, 1, 4, 3}, {1, 1, 4, 2, 1}, {1, 1, 4, 1, 2}, {1, 1, 4, 1, 1, 1}, {1, 1, 3, 4}, {1, 1, 3, 3, 1}, {1, 1, 3, 2, 2}, {1, 1, 3, 2, 1, 1}, {1, 1, 3, 1, 3}, {1, 1, 3, 1, 2, 1}, {1, 1, 3, 1, 1, 2}, {1, 1, 3, 1, 1, 1, 1}, {1, 1, 2, 5}, {1, 1, 2, 4, 1}, {1, 1, 2, 3, 2}, {1, 1, 2, 3, 1, 1}, {1, 1, 2, 2, 3}, {1, 1, 2, 2, 2, 1}, {1, 1, 2, 2, 1, 2}, {1, 1, 2, 2, 1, 1, 1}, {1, 1, 2, 1, 4}, {1, 1, 2, 1, 3, 1}, {1, 1, 2, 1, 2, 2}, {1, 1, 2, 1, 2, 1, 1}, {1, 1, 2, 1, 1, 3}, {1, 1, 2, 1, 1, 2, 1}, {1, 1, 2, 1, 1, 1, 2}, {1, 1, 2, 1, 1, 1, 1, 1}, {1, 1, 1, 6}, {1, 1, 1, 5, 1}, {1, 1, 1, 4, 2}, {1, 1, 1, 4, 1, 1}, {1, 1, 1, 3, 3}, {1, 1, 1, 3, 2, 1}, {1, 1, 1, 3, 1, 2}, {1, 1, 1, 3, 1, 1, 1}, {1, 1, 1, 2, 4}, {1, 1, 1, 2, 3, 1}, {1, 1, 1, 2, 2, 2}, {1, 1, 1, 2, 2, 1, 1}, {1, 1, 1, 2, 1, 3}, {1, 1, 1, 2, 1, 2, 1}, {1, 1, 1, 2, 1, 1, 2}, {1, 1, 1, 2, 1, 1, 1, 1}, {1, 1, 1, 1, 5}, {1, 1, 1, 1, 4, 1}, {1, 1, 1, 1, 3, 2}, {1, 1, 1, 1, 3, 1, 1}, {1, 1, 1, 1, 2, 3}, {1, 1, 1, 1, 2, 2, 1}, {1, 1, 1, 1, 2, 1, 2}, {1, 1, 1, 1, 2, 1, 1, 1}, {1, 1, 1, 1, 1, 4}, {1, 1, 1, 1, 1, 3, 1}, {1, 1, 1, 1, 1, 2, 2}, {1, 1, 1, 1, 1, 2, 1, 1}, {1, 1, 1, 1, 1, 1, 3}, {1, 1, 1, 1, 1, 1, 2, 1}, {1, 1, 1, 1, 1, 1, 1, 2}, {1, 1, 1, 1, 1, 1, 1, 1, 1}}};
    final private int
       maxNumberOfDisplayLines  = breakingLayoutTable.length;                   // The maximum number of lines to use in a text display

    private class Layout                                                        // Possible text layout
     {private class Section                                                     // Section of text in a layout
       {private float width;                                                    // Width of text
        private String text;                                                    // Text
        private boolean breaks;                                                 // Whether text ended in a space
        Section                                                                 //C Layout text as multiple lines
         (final String Text)                                                    //P Text sections
         {breaks = Text.length() > 0 && Text.charAt(Text.length()-1) == ' ';    // Originally ended in space
          text   = Text.trim();                                                 // Text
          width  = paint.measureText(text);                                     // Width of text
         }

        public String toString()                                                //M Describe as a string
         {return "Section(width="+width+", breaks="+breaks+", text="+text+")";
         }
       };

      final private float width;                                                // Width of layout
      final private Stack<Section> sections = new Stack<Section>();             // Text in layout
      final private int breaks;                                                 // Number of breaks in layout

      Layout                                                                    //C Layout text as multiple lines
       (Stack<String> Sections)                                                 //P Text sections
       {float w = 0; int b = 0;
        for(String s : Sections)                                                // Find maximum width and count good breaks
         {if (s == null || s.length() == 0) continue;
          final Section t = new Section(s);                                     // Add new section
          sections.push(t);                                                     // Save section
          if (t.width > w) w = t.width;                                         // Maximum width
          if (t.breaks)    b++;                                                 // Good breaks
         }
        breaks = b;                                                             // Save number of breaks
        width  = w;                                                             // Save maximum width in this layout
       }

      public String toString()                                                  //M Describe as a string
       {return "Layout(width="+width+" size="+size()+", breaks="+
                 breaks+", sections="+sections+")";
       }

      public int size()                                                         //M Number of lines in layout
       {return sections.size();
       }

      float actualScale()                                                       // The actual scale to draw the layout
       {final float
          w  = width,                                                           // Maximum line width
          sx = drawArea.width() / w,                                            // Scaling to fit that width to target area
          sy = drawArea.height()/size()/textSize,
          s = Math.min(sx, sy);                                                 // Scaling to fit that width to target area
        return s;
       }

      float perceivedScale()                                                    // Increase the perceived scale to account for breaks
       {final float n = size(), b = breaks;
        return (1f+(preferBreaksToSpacesFactor-1f)*b/n) * actualScale();
       }
     }

    void addLayout                                                              //M Add lines other than the first line
     (Stack<String> sections)                                                   //P Stack of text sections
     {layouts.push(new Layout(sections));                                       // Create the new layout
     }

    void addLayout                                                              //M Add the first line
     (String text)                                                              //P Text
     {final Stack<String> s = new Stack<String>();
      s.push(text);
      addLayout(s);
     }

    public void setBlockColour                                                  // Draw a rectangle behind the block of text of this colour to provide more contrast.
     (final int c)
     {block.setColor(c);
     }

    private Text                                                                //c Create a text area
      (final String Text,                                                       //P The text to  display
       final float x,                                                           //P Fractional area in which to display the text - horizontal - left
       final float y,                                                           //P Fractional area in which to display the text - horizontal - upper
       final float 𝘅,                                                           //P Fractional area in which to display the text - horizontal - right
       final float 𝘆,                                                           //P Fractional area in which to display the text - horizontal - lower
       final float X,                                                           //P Fractional area in which to display the text - vertical - left
       final float Y,                                                           //P Fractional area in which to display the text - vertical - upper
       final float 𝗫,                                                           //P Fractional area in which to display the text - vertical - right
       final float 𝗬,                                                           //P Fractional area in which to display the text - vertical - lower
       final int   JustifyX,                                                    //P Horizontal justification in x per: L<com.appaapps.LayoutText>
       final int   JustifyY,                                                    //P Horizontal justification in y per: L<com.appaapps.LayoutText>
       final int   Justify𝗫,                                                    //P Vertical justification in x per: L<com.appaapps.LayoutText>
       final int   Justify𝗬)                                                    //P Vertical justification in y per: L<com.appaapps.LayoutText>
     {super(x, y, 𝘅, 𝘆, X, Y, 𝗫, 𝗬);                                            // Create the element containing the drawing of the text
      this.text     = Text.trim();                                              // Text to display
      this.justifyX = JustifyX;                                                 // Justification in X
      this.justifyY = JustifyY;                                                 // Justification in Y
      this.justify𝗫 = Justify𝗫;                                                 // Justification in X
      this.justify𝗬 = Justify𝗬;                                                 // Justification in Y
      paint.setTextSize(textSize);                                              // Unscaled text size
      back.setTextSize(textSize);                                               // Back ground text size
      back.setColor(textBackColour);                                            // Back ground text colour
      back.setStrokeWidth(textStrokeWidth);                                     // Background of text stroke width
      back.setStyle(Paint.Style.FILL_AND_STROKE);                               // Background of text stroke style
      back.setAntiAlias(true);                                                  // Antialias
      theme.p.setTextSize(textSize);                                            // Unscaled text size for theme paint must match the text size of the paint laying out the text
      theme.q.setTextSize(textSize);                                            // Unscaled text size for theme paint must match the text size of the paint laying out the text
      theme.r.setTextSize(textSize);                                            // Unscaled text size for theme paint must match the text size of the paint laying out the text
      theme.p.setAntiAlias(true);                                               // Antialias
      theme.q.setAntiAlias(true);                                               // Antialias
      theme.r.setAntiAlias(true);                                               // Antialias
      block.setColor(0);                                                        // Text is not normally blocked unless requested
      block.setStrokeWidth(textStrokeWidth);                                    // Block outline
      block.setStyle(Paint.Style.STROKE);                                       // Block stroke style
      block.setAntiAlias(true);                                                 // Text is not normally blocked unless requested
      paint.getTextPath(text, 0, text.length(), 0, 0, textPath);                // Layout text with foreground paint
      textPath.computeBounds(textArea, true);                                   // Text bounds

      numberOfChars = text.length();                                            // Length of text string
      widthOfString = paint.measureText(text);                                  // Width of string minus any trailing advance

      if (true)                                                                 //C Layout text as one line
       {final Stack<String> s = new Stack<String>();                            // Awkward - but that is life with Java
        s.push(text);
        addLayout(s);
       }

      for(int lines = 1; lines < maxNumberOfDisplayLines; ++lines)              // Find maximum substring width for each line layout going forwards
       {final float lineWidth = widthOfString / (lines+1);                      // Minimum line width for this layout
        if (true)                                                               // Forwards
         {final Stack<String> texts = new Stack<String>();                      // Text blocks
          int s = 0;                                                            // Current start
          for(int i = 0; i < numberOfChars; ++i)                                // Each character
           {final String t = text.substring(s, i+1);                            // Current extent
            final float  w = paint.measureText(t);                              // Measure width of current extent
            if (w >= lineWidth)                                                 // End if line
             {texts.push(t);                                                    // Text to draw on this line
              s = i+1;                                                          // Start of next line
             }
           }
          texts.push(text.substring(s));                                        // Any remaining text
          addLayout(texts);                                                     // Save layout
         }

        if (false)                                                              // Backwards
         {final Stack<String> texts = new Stack<String>();                      // Text blocks
          int e = numberOfChars;                                                // Current end
          for(int i = numberOfChars-1; i >= 0; --i)                             // Each character
           {final String t = text.substring(i, e);                              // Text to draw on this line
            float w = paint.measureText(t);                                     // Width of current extent
            if (w >= lineWidth)                                                 // End of line
             {texts.insertElementAt(t, 0);                                      // Save text
              e = i;                                                            // Start of next line
             }
           }
          texts.insertElementAt(text.substring(0, e), 0);                       // Any remaining text
          addLayout(texts);                                                     // Save layout
         }

        if (true)                                                               // Breaking at prior space going forwards
         {final Stack<String> texts = new Stack<String>();                      // String sections to draw
          int lastSpace = 0, s = 0;                                             // Last break point for remaining text
          for(int i = 0; i < numberOfChars; ++i)                                // Each character
           {if (text.charAt(i) == ' ') lastSpace = i;                           // Last space
            final String t = text.substring(s, i+1);                            // Current string
            if (paint.measureText(t) >= lineWidth)                              // Reached the end of the line
             {if (lastSpace > 0)                                                // Last space available so break at it
               {texts.push(text.substring(s, lastSpace+1));                     // Text with breaking space so it can be accounted for
                s = lastSpace+1;                                                // Restart
                lastSpace = 0;
               }
              else                                                              // Break in middle of word regardless
               {texts.push(text.substring(s, i+1));                             // Text regardless
                s = i+1;                                                        // Restart
               }
             }
           }
          texts.push(text.substring(s));                                        // Any remaining text
          addLayout(texts);
         }                                                                      // Add layout breaking at spaces

        if (false)                                                              // Breaking at prior space going backwards
         {final Stack<String> texts = new Stack<String>();                      // String sections to draw
          int lastSpace = 0, e = numberOfChars;                                 // Last break point for remaining text
          for(int i = numberOfChars - 1; i >= 0; --i)                           // Each character backwards
           {if (text.charAt(i) == ' ') lastSpace = i;                           // Last space
            final String t = text.substring(i, e);                              // Current string
            if (paint.measureText(t) >= lineWidth)                              // Reached the end of the line
             {if (lastSpace > 0)                                                // Last space available so break at it
               {texts.insertElementAt(text.substring(lastSpace, e), 0);         // Text with breaking space so it can be accounted for
                e = lastSpace;                                                  // Restart
                lastSpace = 0;
               }
              else                                                              // Break in middle of word regardless
               {texts.insertElementAt(text.substring(i, e), 0);                 // Text regardless
                e = i;                                                          // Restart
               }
             }
           }
          texts.insertElementAt(text.substring(0, e), 0);                       // Any remaining text
          addLayout(texts);
         }                                                                      // Add layout breaking at spaces
       }

      if (true)                                                                 // Breaking at all combinations of spaces
       {final Stack<String> texts = new Stack<String>();                        // String sections to draw
        int lastBreak = 0;                                                      // Last break point for remaining text
        for(int i = 0; i < numberOfChars; ++i)                                  // Each character
         {if (text.charAt(i) == ' ')                                            // Space
           {final String t = text.substring(lastBreak, i+1);                    // Current string
            texts.push(t);
            lastBreak = i+1;                                                    // Restart
           }
         }
        texts.push(text.substring(lastBreak, numberOfChars));                   // Any remaining text

        final int N = texts.size();                                             // Number of lines == number of break points - 1
        if (N > 1 && N < breakingLayoutTable.length)                            // We already handle one line adequately, otherwise consider layouts for when we have a breaking layout table
         {final int[][]layouts = breakingLayoutTable[N-1];                      // All the suitable layouts
          for(int i = 0; i < layouts.length; ++i)                               // Each suitable layout
           {final int[]layout = layouts[i];
            int start = 0;
            final Stack<String> Texts = new Stack<String>();                    // Text in lines as dictated by layout
            for(int j = 0; j < layout.length; ++j)                              // Each line
             {String s = "";
              for(int k = 0; k < layout[j]; ++k)
               {s += texts.elementAt(start++);
               }
              Texts.push(s); s = "";                                            // Add line to lines
             }
            addLayout(Texts);
           }
         }
       }                                                                        // Add layout breaking at spaces
     }

    protected void drawElementOnCanvas                                          //O=com.appaapps.Svg.Element2.drawElementOnCanvas Draw the text
     (final Canvas canvas)                                                      //P Canvas to draw on
     {final boolean hnv = canvas.getWidth() > canvas.getHeight();               // Orientation

      if (block.getColor() != 0)                                                // Draw containing biox - ignore if the colour is zero
       {canvas.save();
        canvas.clipRect(drawArea);                                              // Clip box
        canvas.drawRect(drawArea, block);                                       // Draw block
        canvas.restore();
       }

      final int
        jX = hnv ? justifyX : justify𝗫,                                         // Justification in x and y per: L<com.appaapps.LayoutText>
        jY = hnv ? justifyY : justify𝗬;

      final float
        Aw = textArea.width(), Ah = textArea.height(),                          // Dimensions of text
        aw = drawArea.width(), ah = drawArea.height();                          // Dimensions of draw area

//say("AAAA ", text);
      Layout L = null;                                                          // Best layout
      for(Layout l : layouts)                                                   // Find the layout that gives the greatest scaling  there is always at least one = a single line
       {if (L == null || l.perceivedScale() > L.perceivedScale())               // Acceptable scale with allowance for better breakage
         {//say("BBBB ", l);
          L = l;
         }
       }

      if (L != null)                                                            // The single line at worst
       {final Layout layout = L;                                                // The best layout
        final int numberOfLines = layout.size();                                // Number of lines to display
        final float scale = layout.actualScale();                               // Maximum scale factor we can use
//say("CCCC ", layout);

        canvas.save();
        canvas.clipRect(drawArea);                                              // Clip drawing area
        canvas.translate(drawArea.left, drawArea.top);                          // Move to top left corner of drawing area with Y justification
        canvas.scale(scale, scale);                                             // Scale text slightly away from border

        final Paint p = theme.P, q = theme.Q;                                   // Text paints from theme
        p.setTextSize(textSize); q.setTextSize(textSize);                       // Set text size

        for(int i = 0; i < numberOfLines; ++i)                                  // Draw each line
         {final Layout.Section s = layout.sections.elementAt(i);                // Text section
          final String t = s.text;                                              // Text to draw
          final float
            w  = s.width,                                                       // Text width
            lx = (aw / scale - w), ly = (ah / scale / numberOfLines - textSize),// Left over space
            jx = (jX < 0 ? 0 : jX > 0 ? lx : lx / 2),                           // Justify in X
            jy = (jY < 0 ? 0 : jY > 0 ? ly : ly / 2) - paint.descent();         // Justify in Y with allowance for font descent
          canvas.translate(0, textSize);                                        // Down one line
          canvas.drawText(t, jx, jy, back);                                     // Draw text outline background
          canvas.drawText(t, jx, jy, p);                                        // Draw text with first paint
          canvas.drawText(t, jx, jy, q);                                        // Draw text with second paint
         }
        canvas.restore();
       }
     }

    public String type()                                                        //O=com.appaapps.Svg.Element.type Type of this element
     {return "Svg.Text";
     }
    public String toString()                                                    //M Describe as a string
     {return "Svg.Text("+text+")";
     }
   } //C Text

  public Text Text                                                              //M Create a new text element regardless of orientation
   (final String text,                                                          //P The text to display
    final float x,                                                              //P Fractional area in which to display the text - left
    final float y,                                                              //P Fractional area in which to display the text - upper
    final float 𝘅,                                                              //P Fractional area in which to display the text - right
    final float 𝘆,                                                              //P Fractional area in which to display the text - lower
    final int   jX,                                                             //P Justification in x per: L<com.appaapps.LayoutText>
    final int   jY)                                                             //P Justification in y per: L<com.appaapps.LayoutText>
   {final Text t = new Text(text, x, y, 𝘅, 𝘆, x, y, 𝘅, 𝘆, jX, jY, jX, jY);     // Create text element
    push(t);                                                                    // Save it on the stack of elements to be drawn
    return t;
   }

  public Text Text                                                              //M Create a new text element with respect to orientation
   (final String text,                                                          //P The text to display
    final float x,                                                              //P Fractional area in which to display the text - horizontal - left
    final float y,                                                              //P Fractional area in which to display the text - horizontal - upper
    final float 𝘅,                                                              //P Fractional area in which to display the text - horizontal - right
    final float 𝘆,                                                              //P Fractional area in which to display the text - horizontal - lower
    final float X,                                                              //P Fractional area in which to display the text - vertical - left
    final float Y,                                                              //P Fractional area in which to display the text - vertical - upper
    final float 𝗫,                                                              //P Fractional area in which to display the text - vertical - right
    final float 𝗬,                                                              //P Fractional area in which to display the text - vertical - lower
    final int   jX,                                                             //P Justification in x per: L<com.appaapps.LayoutText>
    final int   jY,                                                             //P Justification in y per: L<com.appaapps.LayoutText>
    final int   j𝗫,                                                             //P Justification in x per: L<com.appaapps.LayoutText>
    final int   j𝗬)                                                             //P Justification in y per: L<com.appaapps.LayoutText>
   {final Text t = new Text(text, x, y, 𝘅, 𝘆, X, Y, 𝗫, 𝗬, jX, jY, j𝗫, j𝗬);      // Create text element
    push(t);                                                                    // Save it on the stack of elements to be drawn
    return t;
   }

  public Text Text                                                              //M Create and push a new text element in a quadrant
   (final String text,                                                          //P The text to display
    final int    quadrant)                                                      //P Quadrant, numbered 0-3 clockwise starting at the south east quadrant being the closest to the thumb of a right handed user
   {final float h = 0.5f, q = 0.25f, t = 0.75f;
    return
      quadrant == 0 ? Text(text, h, h, 1, 1,   0, t, 1, 1,   +1, +1,  0, 0):    // SE - 0
      quadrant == 1 ? Text(text, 0, h, h, 1,   0, h, 1, t,   -1, +1,  0, 0):    // SW - 1
      quadrant == 2 ? Text(text, 0, 0, h, h,   0, 0, 1, q,   -1, -1,  0, 0):    // NW - 3
                      Text(text, h, 0, 1, h,   0, q, 1, h,   +1, -1,  0, 0);    // NE - 2
   }

  protected class AFewChars                                                     //C A Few chars is just like text except that the theme is across each character not the entire drawing area
    extends Text                                                                //E So we can draw the text with a theme
   {private AFewChars                                                           //c Create a text area
      (final String text,                                                       //P The text to  display
       final float x,                                                           //P Fractional area in which to display the text - horizontal - left
       final float y,                                                           //P Fractional area in which to display the text - horizontal - upper
       final float 𝘅,                                                           //P Fractional area in which to display the text - horizontal - right
       final float 𝘆,                                                           //P Fractional area in which to display the text - horizontal - lower
       final float X,                                                           //P Fractional area in which to display the text - vertical - left
       final float Y,                                                           //P Fractional area in which to display the text - vertical - upper
       final float 𝗫,                                                           //P Fractional area in which to display the text - vertical - right
       final float 𝗬,                                                           //P Fractional area in which to display the text - vertical - lower
       final int   jX,                                                          //P Justification in x per: L<com.appaapps.LayoutText>
       final int   jY)                                                          //P Justification in y per: L<com.appaapps.LayoutText>
     {super(text, x, y, 𝘅, 𝘆, X, Y, 𝗫, 𝗬, jX, jY, jX, jY);                      // Create the element containing the drawing of the text
     }
    protected float width                                                       //O=com.appaapps.Svg.Element2.width Approximate width of object before scaling is the drawn length of the string
     (Canvas canvas)                                                            //P Canvas that will be drawn on
     {return textSize * text.length();
     }
    protected float height                                                      //O=com.appaapps.Svg.Element2.height Approximate width of object before scaling is one character
     (Canvas canvas)                                                            //P Canvas that will be drawn on//P Canvas that will be drawn on
     {return textSize;
     }
   } //C AFewChars

  public AFewChars AFewChars                                                    //M Create a new AFewChars element regardless of orientation
   (final String text,                                                          //P The small amount of text to display
    final float x,                                                              //P Fractional area in which to display the text - left
    final float y,                                                              //P Fractional area in which to display the text - upper
    final float 𝘅,                                                              //P Fractional area in which to display the text - right
    final float 𝘆,                                                              //P Fractional area in which to display the text - lower
    final int   jX,                                                             //P Justification in x per: L<com.appaapps.LayoutText>
    final int   jY)                                                             //P Justification in y per: L<com.appaapps.LayoutText>
   {final AFewChars t = new AFewChars(text, x, y, 𝘅, 𝘆, x, y, 𝘅, 𝘆, jX, jY);    // Create AFewCharst element
    push(t);                                                                    // Save it on the stack of elements to be drawn
    return t;
   }

  public AFewChars AFewChars                                                    //M Create a new AFewChars element with respect to orientation
   (final String text,                                                          //P The small amount of text to display
    final float x,                                                              //P Fractional area in which to display the text - horizontal - left
    final float y,                                                              //P Fractional area in which to display the text - horizontal - upper
    final float 𝘅,                                                              //P Fractional area in which to display the text - horizontal - right
    final float 𝘆,                                                              //P Fractional area in which to display the text - horizontal - lower
    final float X,                                                              //P Fractional area in which to display the text - vertical - left
    final float Y,                                                              //P Fractional area in which to display the text - vertical - upper
    final float 𝗫,                                                              //P Fractional area in which to display the text - vertical - right
    final float 𝗬,                                                              //P Fractional area in which to display the text - vertical - lower
    final int   jX,                                                             //P Justification in x per: L<com.appaapps.LayoutText>
    final int   jY)                                                             //P Justification in y per: L<com.appaapps.LayoutText>
   {final AFewChars t = new AFewChars(text, x, y, 𝘅, 𝘆, X, Y, 𝗫, 𝗬, jX, jY);    // Create AFewChars element
    push(t);                                                                    // Save it on the stack of elements to be drawn
    return t;
   }

  public class Image                                                            //C Draw a bitmap image - move it around to show it all within the space available
    extends Element                                                             //E No special paint effects needed
   {private final RectF picture  = new RectF();                                 // The dimensions of the bitmap
    private final double
      phase     = Math.PI * random.nextDouble(),                                // Bitmap display phase offset
      startTime = Time.secs(),                                                  // Number of seconds for this image to glide across its display area, start time for this animation
      glideTime;                                                                // Number of seconds for this image to glide across its display area, start time for this animation
    private final PhotoBytes.Draw bitmap;                                       // Decompress the bitmap thread
    private PointF pointOfInterest = null;                                      // Point of interest represented as fractional coordinates

    private Image                                                               //c Fraction coordinates of corners of drawing area
     (final PhotoBytes photoBytes,                                              //P Bitmap containing image
      final float x,                                                            //P Fraction coordinates of left edge  - horizontal
      final float y,                                                            //P Fraction coordinates of upper edge - horizontal
      final float 𝘅,                                                            //P Fraction coordinates of right edge - horizontal
      final float 𝘆,                                                            //P Fraction coordinates of lower edge - horizontal
      final float X,                                                            //P Fraction coordinates of left edge  - vertical
      final float Y,                                                            //P Fraction coordinates of upper edge - vertical
      final float 𝗫,                                                            //P Fraction coordinates of right edge - vertical
      final float 𝗬,                                                            //P Fraction coordinates of lower edge - vertical
      final int   inverseFractionalArea)                                        //P The approximate inverse of the fraction of the area of the screen covered by  this image so that the image can be sub sampled appropriately if necessary
     {super(x, y, 𝘅, 𝘆, X, Y, 𝗫, 𝗬);                                            // Create the element that will draw the bitmap
      if (screenShotMode)                                                       // Slow and steady glide for screen shots
       {this.glideTime = 40;
       }
      else
       {final double g = Svg.this.glideTime;
        this.glideTime = g + g * Math.abs(random.nextGaussian());
       }
      bitmap = photoBytes.prepare                                               // Unpack bytes to create bitmap
       (picture, Maths.roundUpToPowerOfTwo(inverseFractionalArea));
      bitmap.start();
      prepare.push(bitmap);                                                     // So we can wait for all the images to be prepared before using the svg
     }

    protected void drawElementOnCanvas                                          //O=com.appaapps.Svg.Element2.drawElementOnCanvas Draw a bitmap
     (final Canvas canvas)                                                      //P Canvas to draw on
     {final PointF
        p = pointOfInterest;                                                    // Finalize point of interest
      final Mover
        i = imageMover;                                                         // Finalize image mover
      final boolean
        𝗶 = i != null && i.containingImage == this,                             // Finalize presence of image mover applicable to this image
        𝗽 = p != null;                                                          // Finalize presence of point of interest
      final float
        pw = picture.width(),  dw = drawArea.width(),                           // Width of picture and draw area
        ph = picture.height(), dh = drawArea.height(),                          // Height of picture and draw area
        sn = (float)Math.sin((Time.secs() - startTime) /                        // Sine of time
                             glideTime * Math.PI + phase),
        sf = sn * sn,                                                           // Sine squared for smooth lift off, hold and return
        px = 𝗶 ? i.position.x : 𝗽 ? pointOfInterest.x : sf,                     // Position adjustment in x
        py = 𝗶 ? i.position.y : 𝗽 ? pointOfInterest.y : sf,                     // Position adjustment in y
        mg = 𝗶 ? i.magnification  : 1,                                          // Additional magnification requested by image mover
        scale = maxScale(picture, drawArea),                                    // Scale factor from image to drawing area
        dx = 𝗶 ? px * pw * scale : px * (pw * scale - dw / mg),                 // Amount of free space left over after image has been scaled to fit the drawing area
        dy = 𝗶 ? py * ph * scale : py * (ph * scale - dh / mg),
        cx = drawArea.left - dx,                                                // Screen coordinates of the top left corner of the image
        cy = drawArea.top  - dy;

      canvas.save();
      canvas.clipRect(drawArea);                                                // Clip to area occupied by image

      canvas.translate(cx, cy);                                                 // Set origin at the top left corner of the  image
      canvas.scale(scale, scale);                                               // Scale the photo

      if (𝗶)                                                                    // Image mover request
       {final float
          x = (i.mx - cx) / scale,                                              // Coordinates of the center of magnification relative to the top left corner of the image after initial scaling and translation
          y = (i.my - cy) / scale,
          M = scale * mg,                                                       // Combined magnification
          x1 = i.mx + (cx - i.mx) * mg - drawArea.left,                         // Position of top left corner after scaling relative to top left corner of draw area - if it becomes positive we will see black on the left hand side of the image
          y1 = i.my + (cy - i.my) * mg - drawArea.top,                          // Positive means black at top
          x2 = x1 + pw * M - dw,                                                // Positive means we have black at the right
          y2 = y1 + ph * M - dh;                                                // Positive means we have black at the bottom

        if (mg > 1) canvas.scale(mg, mg, x, y);                                 // Apply magnification if any is present

        if      (x1 > 0)                                                        // Avoid black left
         {canvas.translate(   -x1 / M, 0);
          i.position.x += x1 / pw / overDragRelaxRate;
         }
        else if (x2 < 0)                                                        // Avoid black right
         {canvas.translate(   -x2 / M, 0);
          i.position.x += x2 / pw / overDragRelaxRate;
         }
        if      (y1 > 0)                                                        // Avoid black top
         {canvas.translate(0, -y1 / M);
          i.position.y += y1 / ph / overDragRelaxRate;
         }
        else if (y2 < 0)                                                        // Avoid black bottom
         {canvas.translate(0, -y2 / M);
          i.position.y += y2 / ph / overDragRelaxRate;
         }
       }

      bitmap.draw(canvas);                                                      // Draw photo
      canvas.restore();
     }

    public void drawCircle(Canvas c, int color, float x, float y, float r)
     {final Paint paint = new Paint();
      paint.setColor(color);
      paint.setStyle(Paint.Style.FILL_AND_STROKE);
      c.drawCircle(x, y, r, paint);
     }

    public void pointOfInterest                                                 //M Set a points of interest in the image as fractions left to right, top to bottom
     (PointF pointOfInterest)                                                   //P Point of interest represented as fractional coordinates
     {pointOfInterest.x = fractionClamp(pointOfInterest.x);
      pointOfInterest.y = fractionClamp(pointOfInterest.y);
      this.pointOfInterest = pointOfInterest;
     }

    public void resetPointOfInterest()                                          //M Reset the point of interest
     {this.pointOfInterest = null;
     }

    class Mover extends Thread                                                  //C Image move request
     {final Image
        containingImage       = Image.this;                                     // The image we are contained in
      final PointF
        position              = new PointF(0, 0);                               // Fraction of image to move in x
      final double
        magnificationStepTime = 0.01,                                           // Time between magnification steps
        magnificationWaitTime = 1;                                              // Time the user has to wait motionless for magnification or demagnification to begin
      final float
        magnificationPerStep  = 1.005f,                                         // Magnification factor on each step,
        maximumMagnification  = 4,                                              // Maximum magnification
        maximumMoveIncrease   =  1f / 64,                                       // Maximum fraction of the screen diagonal the user can move from the touch point and still get magnification
        minimumMoveDecrease   =  1f / 16,                                       // Minimum fraction of the screen diagonal the user must move from the touch point to demagnification
        minimumMagnification  = 1,                                              // Minimum magnification
        movementScale         = 4;                                              // Scale movement on screen to movement in image
      float
        magnification         = 1,                                              // Magnification scale factor
        mx                    = 0,                                              // Pixel center of magnification in x
        my                    = 0;                                              // Pixel center of magnification in y
      long
        lastIncreaseLoopIndex = 0,                                              // Last loop number on which magnification was possible
        lastDecreaseLoopIndex = 0;                                              // Last loop number on which demagnification was possible

      public void run()
       {for(long i = 0; imageMover != null; ++i)                                // Tracking loop
         {Svg.sleep(magnificationStepTime);
          if (pressTime != null)                                                // Pressing
           {if (Time.secs() - dragTimeLast > magnificationWaitTime)             // User has waited motionless long enough for magnification or demagnification to begin
             {if      (dragFraction < maximumMoveIncrease)                      // Close to the touch point and waited motionless for long enough - increase magnification
               {if (i == lastIncreaseLoopIndex + 1)                             // Pressing at start and end of loop
                 {updateMagnification(magnificationPerStep);                    // Increase magnification
                 }
                lastIncreaseLoopIndex = i;                                      // Conditions for magnification appertained
               }
              else if (dragFraction > minimumMoveDecrease)                      // Far from the touch point and waited motionless for long enough - decrease magnification
               {if (i == lastDecreaseLoopIndex + 1)                             // Pressing at start and end of loop
                 {updateMagnification(1f / magnificationPerStep);               // Decrease magnification
                 }
                lastDecreaseLoopIndex = i;                                      // Conditions for magnification appertained
               }
             }
           }
         }
       }

      private void updateMagnification                                          //M Update magnification - see /home/phil/perl/z/centerOfExpansion/test.pl
       (final float m)                                                          //P Magnification
       {final double                                                            // Vector from last center of expansion to latest center of expansion
          dx = x - mx,
          dy = y - my,                                                          // Distance between centers of expansion
          d  = Math.hypot(dx, dy),                                              // Distance between centers of expansion
          l  = (d - d * m) / (1 - m * magnification);                           // Distance along joining line

        if (d > 1e-6)                                                           //  Distance is non zero so merge two magnification centers
         {final double
            f  = l / d,                                                         // Fraction along line
            fx = dx * f,                                                        // Fraction along line
            fy = dy * f;                                                        // Fraction along line

          mx += (float)fx;                                                      // New magnification center
          my += (float)fy;
         }

        final float M = magnification * m;                                      // Clamp magnification to limits
        if      (M > maximumMagnification) magnification = maximumMagnification;
        else if (M < minimumMagnification) magnification = minimumMagnification;
        else                               magnification = M;
       }

      public void updateImageOffset()                                           //M Update image offset within its draw area via its point of interest
       {float
          r = movementScale / lastCanvasWidth  / magnification,                 // Motion on screen to motion in image
          x = position.x - dx * r,                                              // Negative so we manipulate the contents if the photo rather than the viewer
          y = position.y - dy * r;
        position.x = x; position.y = y;                                         // Move the image to the desired position
       }
     } //C Mover

   public String type()                                                         //O=com.appaapps.Svg.Element.type Type of this element
     {return "Svg.Image";
     }
   } //C Image

  public Image Image                                                            //M Create a new image regardless of orientation
   (PhotoBytes bitmap,                                                          //P Bitmap containing image
    final float x,                                                              //P Fraction coordinates of left edge
    final float y,                                                              //P Fraction coordinates of upper edge
    final float 𝘅,                                                              //P Fraction coordinates of right edge
    final float 𝘆,                                                              //P Fraction coordinates of lower edge
    final int   inverseFractionalArea)                                          //P The approximate inverse of the fraction of the area of the screen covered by  this image so that the image can be sub sampled appropriately if necessary
   {final Image i = new Image(bitmap, x, y, 𝘅, 𝘆, x, y, 𝘅, 𝘆,                   // Create the image
                              inverseFractionalArea);
    push(i);                                                                    // Save the image on the stack of elements to be drawn
    return i;
   }

  public Image Image                                                            //M Create a new image with respect to orientation
   (PhotoBytes bitmap,                                                          //P Bitmap containing image
    final float x,                                                              //P Fraction coordinates of left edge  - horizontal
    final float y,                                                              //P Fraction coordinates of upper edge - horizontal
    final float 𝘅,                                                              //P Fraction coordinates of right edge - horizontal
    final float 𝘆,                                                              //P Fraction coordinates of lower edge - horizontal
    final float X,                                                              //P Fraction coordinates of left edge  - vertical
    final float Y,                                                              //P Fraction coordinates of upper edge - vertical
    final float 𝗫,                                                              //P Fraction coordinates of right edge - vertical
    final float 𝗬,                                                              //P Fraction coordinates of lower edge - vertical
    final int   inverseFractionalArea)                                          //P The approximate inverse of the fraction of the area of the screen covered by  this image so that the image can be sub sampled appropriately if necessary
   {final Image i = new Image(bitmap, x, y, 𝘅, 𝘆, X, Y, 𝗫, 𝗬,                   // Create the image
                              inverseFractionalArea);
    push(i);                                                                    // Save the image on the stack of elements to be drawn
    return i;
   }

  public Image Image                                                            //M Create and push a new image in the specified quadrant
   (PhotoBytes bitmap,                                                          //P Bitmap containing image
    final int quadrant)                                                         //P Quadrant, numbered 0-3 clockwise starting at the south east quadrant being the closest to the thumb of a right handed user
   {final float h = 0.5f, q = 0.25f, t = 0.75f;
    return
      quadrant == 0 ? Image(bitmap,  h, h, 1, 1,  0, t, 1, 1,  4):              // SE - 0
      quadrant == 1 ? Image(bitmap,  0, h, h, 1,  0, h, 1, t,  4):              // SW - 1
      quadrant == 2 ? Image(bitmap,  0, 0, h, h,  0, 0, 1, q,  4):              // NW - 3
                      Image(bitmap,  h, 0, 1, h,  0, q, 1, h,  4);              // NE - 2
   }

  public void ImageMover                                                        //M Instruction to move the image
   (final Image image)                                                          //P Image to move
   {imageMover = image.new Mover();                                             // Set the image mover active
    imageMover.start();                                                         // Run the image mover active
   }

  public void removeImageMover()                                                //M Remove the current image mover
   {imageMover = null;                                                          // Remove the image mover
   }

  public class CompassRose                                                      //C Draw an compass rose command selector
    extends Element2                                                            //E Two paint effects
   {public final Path path = new Path();                                        // One segment of the compass rose
    public final String[]lines = new String[8];                                 // The text for each segment
    public final float
      angle = 45f,                                                              // Angle of each segment
      sa    = (float)Math.sin(Math.PI / 8),                                     // Sine(22.5 degrees)
      ca    = (float)Math.cos(Math.PI / 8);                                     // Cosine(22.5 degrees)
    public Cmd[]commands  = new Cmd[8];                                         // Commands for the compass rose
    public Integer octant = null;                                               // Current octant we are in or null if we are not in any octant
    public float X, Y;                                                          // Coordinates of compass rose center
    private int numberOfActiveCompassRoseCommands = 0;                          // Number of active commands

    private CompassRose()                                                       //c Create an compass rose with the specified commands
     {super(0, 0, 1, 1, 0, 0, 1, 1);                                            // Create the path that will draw one segment of the compass rose
      path.moveTo( 0,   0);
      path.lineTo(ca,  sa);
      path.lineTo(ca, -sa);
      path.close();
     }

    protected void drawElementOnCanvas                                          //O=com.appaapps.Svg.Element2.drawElementOnCanvas Draw a bitmap
     (final Canvas canvas)                                                      //P Canvas to draw on
     {canvas.save();
      final double
        w = canvas.getWidth(), h = canvas.getHeight(),                          // Canvas dimensions
        d = Math.min(w, h),    D = Math.max(w, h),                              // Minimum and maximum dimensions of the canvas
        f = dt < compassRoseGrowTime ? dt / compassRoseGrowTime : 1,            // Octoline fraction
        scale = d / 2  * Math.sin(Math.PI / 2 * f);                             // Scale factor dependent on time and size of canvas with sinusoidal tail off.  The user should press in the central third to be sure of seeing all of the compass rose when it has fully grown
      final Paint                                                               // Theme paints
        p = theme.p, q = theme.q;

      if (true)                                                                 // Octoline background
       {canvas.save();
        canvas.translate(X, Y);                                                 // Touch location
        canvas.scale((float)scale, (float)scale);                               // Size
        canvas.drawPath(path, p); canvas.rotate(angle);                         // Draw segments in alternating colours
        canvas.drawPath(path, q); canvas.rotate(angle);
        canvas.drawPath(path, p); canvas.rotate(angle);
        canvas.drawPath(path, q); canvas.rotate(angle);
        canvas.drawPath(path, p); canvas.rotate(angle);
        canvas.drawPath(path, q); canvas.rotate(angle);
        canvas.drawPath(path, p); canvas.rotate(angle);
        canvas.drawPath(path, q); canvas.rotate(angle);
        canvas.drawPath(path, p);
        canvas.restore();
       }

      if (true)                                                                 // Draw compass rose commands
       {for(Cmd c: commands)                                                    // Draw each command
         {if (c == null) continue;

          final float S = (float)scale, s = S / 2;                              // Scale for octant, scale for text
          final Text  t = c.text;                                               // Text to draw
          attachRectangle(c.position, canvas, S, X, Y, t);                      // Set the target area for the text of the command
          t.setBlockColour(octant != null && octant == c.position ?             // Highlight the octant command text if this is the currently selected octant
              0x80ffffff : 0);
          c.text.drawElement(canvas);
         }
       }
     }

    private void attachRectangle                                                //M Position a rectangle against a compass point
     (final int octant,                                                         //P Octant
      final Canvas canvas,                                                      //P Canvas we are going to draw on
      final float scale,                                                        //P Scale
      final float x,                                                            //P X coordinate of center of compassRose
      final float y,                                                            //P Y coordinate of center of compassRose
      final Text text)                                                          //P Text element whose target is to be set
     {final Rect   r = or[octant];                                              // Adjustment for rectangle in this octant
      final PointF c = oc[octant];                                              // Center of octant
      final float  u = compassRoseRectUnits, b = ob, cx = c.x, cy = c.y;        // Finalize for optimization
      if (text != null)                                                         // A command has been set
       {final RectF  h = text.targetH, v = text.targetV;
        h.set(x + scale * (oi * cx + b * r.left   / u),                         // Position rectangle around center at current scale
              y + scale * (oi * cy + b * r.top    / u),
              x + scale * (oi * cx + b * r.right  / u),
              y + scale * (oi * cy + b * r.bottom / u));
        h.sort();
        v.set(h);
        fractionateRectangle(canvas, h);                                        // Make the target a fraction of the canvas
        fractionateRectangle(canvas, v);
       }
     }

    protected float width                                                       //O=com.appaapps.Svg.Element2.width Approximate width of object before scaling is the drawn length of the string
     (Canvas canvas)                                                            //P Canvas that will be drawn on
     {return 1;                                                                 // Unscaled width of compass rose
     }
    protected float height                                                      //O=com.appaapps.Svg.Element2.height Approximate width of object before scaling is one character
     (Canvas canvas)                                                            //P Canvas that will be drawn on//P Canvas that will be drawn on
     {return 1;                                                                 // Unscaled height of compass rose
     }

    public int octant()                                                         //M Octant we are in of the compass rose
     {final double r = Math.hypot(x-X,           y-Y);                          // Length of a radius from center of compassRose to current point
      double D =       Math.hypot(x-X-r*oc[0].x, y-Y-r*oc[0].y);                // Distance to first point from first vertex
      int    N = 0;                                                             // Current vertex to which we are known to be nearest
      for(int i = 1; i < 8; ++i)                                                // Find the nearest vertex to the point if it is not the first vertex
       {final double d = Math.hypot(x-X-r*oc[i].x, y-Y-r*oc[i].y);              // Distance to current vertex
        if (d < D) {D = d; N = i;}                                              // Nearer vertex
       }
      return N;                                                                 // Return number of nearest vertex thus the octant
     }

    public void clearCompassRoseCmds()                                          // Clear all the commands
     {for(int i = 0; i < commands.length; ++i)                                  // Each command slot
       {commands[i] = null;
       }
      numberOfActiveCompassRoseCommands = 0;                                    // Reset number of active commands
     }

    public class Cmd                                                            //C A command that the compass rose can display
     {public  final String cmd;                                                 // Command name
      public  final int position;                                               // Slot position 0 - 7 the command is to occupy
      public  final Runnable run;                                               // Thread whose run() method will be called to execute the command if the user selects it
      public  final Text text;                                                  // Text element to draw text of command
      public  final boolean subMenu;                                            // Command creates a sub menu if true
      private Cmd                                                               //c Menu item possibly with a sub menu
       (final String   cmd,                                                     //P Command name
        final int      position,                                                //P Slot position 0 - 7 the command is to occupy
        final boolean  subMenu,                                                 //P True - this menu items creates a sub menu
        final Runnable run)                                                     //P Thread whose run() method will be called to execute the command if the user selects it
       {this.cmd      = cmd;                                                    // Command name
        this.position = position % commands.length;                             // Command slot position
        this.subMenu  = subMenu;                                                // Creates sub menu
        this.run      = run;                                                    // Method to run
        commands[this.position] = this;                                         // Add to array of commands for this compass rose
        numberOfActiveCompassRoseCommands++;                                    // Count number of active commands - assumes that we only set each command once
        final Point j = oj[position];                                           // Justification for each position
        text = new Text(cmd, 0,0,1,1, 0,0,1,1, j.x, j.y, j.x, j.y);             // Text element to display the command
       }
      private Cmd                                                               //c Menu items that does not create a sub menu
       (final String   cmd,                                                     //P Command name
        final int      position,                                                //P Slot position 0 - 7 the command is to occupy
        final Runnable run)                                                     //P Thread whose run() method will be called to execute the command if the user selects it
       {this(cmd, position, false, run);
       }
      public Cmd setTextTheme                                                   //M Set a theme for the text of the command
       (final Themes.Theme theme)                                               //P Theme to use
       {text.setTheme(theme);
        return this;
       }
     } //C Cmd

    public Svg createPageMenu()                                                 //M Create a page menu to represent the Rose
     {final int nx = 3, ny = 3;
       final int[][]roseToPage = {{1,0},{0,0},{0,1},{0,2},{1,2},{2,2},{2,1},{2,0}};
      final double
        Dx = 1d / nx, Dy = 1d / ny,
        border = 0.10,
        dx = Dx * border, dy = Dx * border;

      final Svg s = new Svg();                                                  // Page menu Svg
      if (true)                                                                 // Back command in center
       {final double x = Dx, y = Dy, X = x + Dx, Y = y + Dy;
        final Svg.Text t = s.Text
         ("back", f(x+dx), f(y+dy), f(X-dx), f(Y-dy), 0, 0);
        t.tapAction(new Runnable()
         {public void run()
           {pageMenuActive = false;                                             // Return to normal play
           }
         });
       }
      for(int n = 0; n < roseToPage.length; ++n)                                // Other commands around the edge
       {final Cmd cmd = commands[n];
        final int
          i = roseToPage[n][0],
          j = roseToPage[n][1];
        final double
          x = i * Dx, y = j * Dy,
          X = x + Dx, Y = y + Dy;
        if (cmd != null)
         {final Svg.Text t = s.Text                                             // Create a text element to display the command name
           (cmd.cmd, f(x+dx), f(y+dy), f(X-dx), f(Y-dy), 0, 0);
          t.setTheme(cmd.text.theme);                                           // Copy the theme from the original Text created for the Rose
          t.tapAction(new Runnable()                                            // Add a tap action
           {public void run()
             {cmd.run.run();                                                    // Run the page menu action
              if (cmd.subMenu)                                                  // Construct new page menu if sub menu
               {svgPageMenu = createPageMenu();                                 // Replace the existing page menu
               }
              else                                                              // Force rebuild of page menu on next tap else we will continue to see this one.
               {svgPageMenu = null;
               }
              pageMenuActive = cmd.subMenu;                                     // Keep page menu active
             }
           });
         }
       }
      return s;
     }
   } //C Octoline

  public CompassRose.Cmd setCompassRoseCmd                                      //M Create an compass rose cmd that does create a sub menu
   (final String   name,                                                        //P Display this name to identify the command
    final int      number,                                                      //P Place the command in this slot - replacing any other command there
    final boolean  subMenu,                                                     //P True - the command creates a sub menu
    final Runnable run)                                                         //P Call the run() method of this thread to execute the command if the user selects it
   {return compassRose.new Cmd(name, number, subMenu, run);                     // Create command
   }
  public CompassRose.Cmd setCompassRoseCmd                                      //M Create an compass rose cmd that does not create a sub menu
   (final String   name,                                                        //P Display this name to identify the command
    final int      number,                                                      //P Place the command in this slot - replacing any other command there
    final Runnable run)                                                         //P Call the run() method of this thread to execute the command if the user selects it
   {return setCompassRoseCmd(name, number, false, run);                         // Create command
   }

  public void clearCompassRoseCmds()                                            //M Remove all commands from the ocotoline
   {final CompassRose o = compassRose;                                          // Lock onto the compassRose
    if (o != null) o.clearCompassRoseCmds();                                    // Clear all the compass rose commands
   }

  static private float maxScale                                                 //M Maximum scale factor from first specified rectangle to the second
   (final RectF source,                                                         //P Source rectangle
    final RectF target)                                                         //P Target rectangle
   {final float
      w = source.width(),                                                       //P Width  of input rectangle
      h = source.height(),                                                      //P Height of input rectangle
      W = target.width(),                                                       //P Width  of output rectangle
      H = target.height(),                                                      //P Height of output rectangle
      x = Math.abs(W / w), y = Math.abs(H / h);
    return x > y ? x : y;
   }

  static Bitmap testImage()                                                     //M Create a test image
   {final int width = 256, height = 256;
    final Bitmap b=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);

    for  (int i = 0; i < 256; ++i)                                              // Draw the bitmap
     {for(int j = 0; j < 256; ++j)
       {b.setPixel(i, j, ColoursTransformed.colour(i, j, i * j % 255));
       }
     }
    return b;
   }

  private static void fractionateRectangle                                      //M Convert a rectangle into fractions of a canvas
   (final Canvas canvas,                                                        //P Canvas on which the rectangle will be drawn
    final RectF  rectangle)                                                     //P Rectangle to fractionate
   {final float w = canvas.getWidth(), h = canvas.getHeight();                  // Canvas dimensions
    final RectF r = rectangle;
    r.left /= w; r.right  /= w;
    r.top  /= h; r.bottom /= h;
   }


  public void press                                                             //M Start pressevent
   (final float x,                                                              //P X coordinate of press
    final float y)                                                              //P Y coordinate of press
   {this.X = this.x = compassRose.X = x;                                        // Save new press position of X
    this.Y = this.y = compassRose.Y = y;                                        // Save new press position of Y
    updateDrag(x, y);                                                           // Update values dependent on drag position
    pressedElement  = findContainingElement(x, y);                              // Pressed element                                    // Find the element under the press
    releasedElement = null;                                                     // Released element
   }

  public void drag                                                              //M Update the current touch position
   (final float x,                                                              //P X coordinate of touch
    final float y)                                                              //P Y coordinate of touch
   {updateDrag(x, y);                                                           // Update values dependent on drag position
   }

  public boolean tapNotTouch()                                                  // Fast enough to be a tap
   {return dragTimeTotal < tapNotTouchTime;
   }

  public boolean movedEnoughToBeASwipe()                                        // Moved enough to be a swipe
   {return dragFraction > swipeFraction;
   }

  public void release                                                           //M Finished with this touch
   (final float x,                                                              //P X coordinate of release
    final float y)                                                              //P Y coordinate of release
   {updateDrag(x, y);                                                           // Update values dependent on drag position
    releasedElement = findContainingElement(x, y);                              // Released element
    pressTime = null;                                                           // No longer pressing
    final boolean                                                               // Motion characteristics
      moved = movedEnoughToBeASwipe(),                                          // Moved enough to be a swipe
      quick = tapNotTouch();                                                    // Fast enough to be a tap

    if (!moved && quick)                                                        // Call the tap method on a new thread if the interaction was quick with not much movement indicating a tap
     {final Svg s = pageMenuActive ? svgPageMenu : this;                        // The menu page overlays the displayed content
      final Element  e = s.findContainingElementWithActionTap(x, y);            // Element user released over that has a tap action
      final Runnable r = e != null ? e.actionTap : null;                        // Runnable associated with released element
      startThread(r != null ? r : userTapped);                                  // Run specific action or general user tapped action if none available
     }
    else if (menuMode == MenuMode.Rose)                                         // Decode Compass Rose
     {if (moved && compassRose.octant != null)                                  // Run the command for the selected octant
       {final int o = compassRose.octant;                                       // The not null octant chosen
        final CompassRose.Cmd command = compassRose.commands[o];
        if (command != null)                                                    // Command exists
         {startThread(command.run);                                             // Run the octant's command
         }
        else                                                                    // No command supplied for this octant
         {startThread(userSelectedAnOctantWithNoCommand);
         }
       }
      else                                                                      // Otherwise it was a swipe
       {startThread(userSelectedAnOctantThenCancelledOrMovedButNotEnough);
       }
     }
    else if (menuMode == MenuMode.Page)                                         // Decode page menu
     {runPageMenuTapAction(x, y);                                               // Run the command for the selected octant
     }
   }

  private void updateDrag                                                       //M Update values dependent on drag position
   (final float x,                                                              //P X coordinate of press
    final float y)                                                              //P Y coordinate of press
   {dx = x - this.x; dy = y - this.y;                                           // Motion from last drag point
    this.x = x; this.y = y;                                                     // Record latest press position
    if (pressTime == null) pressTime = Time.secs();                             // Save latest start time
    distanceFromTouch = (float)Math.hypot(x - X, y - Y);                        // Distance in pixels from touch point
    final float d = (float)Math.hypot(lastCanvasWidth, lastCanvasHeight);       // Diagonal size of last canvas drawn
    dragFraction  = d != 0 ? distanceFromTouch / d : 0;                         // Fraction of last canvas diagonal of straight line drag distance
    dragTimeLast  = Time.secs();                                                // Time of last drag
    dragTimeTotal = dragTimeLast - pressTime;                                   // Time taken by drag so far in seconds
    final CompassRose o = compassRose;                                          // Process compass rose command
    if (o != null)
     {o.octant = dragFraction > swipeFraction ? o.octant() : null;              // Octant we are in
     }
    final Image.Mover i = imageMover;                                           // Image move request
    if (i != null) i.updateImageOffset();                                       // Update image mover with drag
   }

  public Element findContainingElement                                          //M Find the first element that contains this point
   (final float x,                                                              //P X coordinate
    final float y)                                                              //P Y coordinate
   {for(Element e: elements)
     {if (e.visible && e.drawArea.contains(x, y)) return e;
     }
    return null;                                                                // No such element
   }

  public Element findContainingElementWithName                                  //M Find the first element that contains this point and has a name assigned
   (final float x,                                                              //P X coordinate
    final float y)                                                              //P Y coordinate
   {for(Element e: elements)
     {if (e.visible && e.drawArea.contains(x, y) && e.name != null) return e;
     }
    return null;                                                                // No such element
   }

  public Element findContainingElementWithActionTap                             //M Find the first element that contains this point and has a tap action assigned
   (final float x,                                                              //P X coordinate
    final float y)                                                              //P Y coordinate
   {for(Element e: elements)
     {if (e.visible && e.drawArea.contains(x, y) && e.actionTap!=null) return e;
     }
    return null;                                                                // No such element
   }

  public float fractionClamp                                                    //M Clamp a number to the range 0 - 1
   (final float n)                                                              //P Value to be clamped
   {//if (n > 1) return 1;
    //if (n < 0) return 0;
    return n;
   }

  private Thread startThread                                                    //M Start a thread  to run a thread's run() method
   (final Runnable r)                                                           //P Runnable to run
   {if (r != null)                                                              // Thread has been supplied
     {final Thread t = new Thread(r);                                           // Create a new thread
      t.start();                                                                // Start the new thread
      return t;                                                                 // Return the new thread
     }
    return null;                                                                // Return null if no thread supplied
   }

  private static void sleep                                                     //M Sleep for the specified duration
   (final double duration)                                                      //P Sleep duration expressed in seconds
   {try
     {final long dt = (long)(duration * 1000);
      Thread.sleep(dt);
     }
    catch(Exception e) {}
   }

  private static float f                                                        //M Convert to float
   (final double d)                                                             //P Double to convert
   {return (float)d;
   }

  static void main(String[] args)
   {final Svg s = new Svg();
    s.Rectangle(0, 0, 100, 100);
    s.Text("H", 0, 0, 100, 100, 0, 0);
    //s.Image(testImage(), 0, 0, 100, 100);
   }

  private static void lll(Object...O) {final StringBuilder b = new StringBuilder(); for(Object o: O) b.append(o.toString()); System.err.print(b.toString()+"\n");}
  private static void say(Object...O) {com.appaapps.Log.say(O);}
 } //C Svg

// Octoline highlights selected text

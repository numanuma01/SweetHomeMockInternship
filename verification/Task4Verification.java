import com.eteks.sweethome3d.io.DefaultUserPreferences;
import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.Level;
import com.eteks.sweethome3d.model.Selectable;
import com.eteks.sweethome3d.model.Wall;
import com.eteks.sweethome3d.swing.PlanComponent;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.print.PageFormat;
import java.awt.print.Paper;
import java.awt.print.Printable;
import java.util.List;

public class Task4Verification {
  private static int checks;

  private static class TestPlan extends PlanComponent {
    private final Home home;
    private Level printedLevel;
    private boolean fail;

    TestPlan(Home home) {
      super(home, new DefaultUserPreferences(), null);
      this.home = home;
    }

    @Override
    protected List<Selectable> getPaintedItems() {
      if (this.home != null) {
        this.printedLevel = this.home.getSelectedLevel();
        if (this.fail) {
          throw new IllegalStateException("Test rendering failure");
        }
      }
      return super.getPaintedItems();
    }
  }

  public static void main(String[] args) {
    Home home = new Home();
    for (int index = 0; index < 3; index++) {
      Level level = new Level("Level " + index, index * 250f, 10f, 240f);
      home.addLevel(level);
      Wall wall = new Wall(0, 0, 200, 0, 10, 240);
      home.addWall(wall);
      wall.setLevel(level);
    }
    home.setSelectedLevel(home.getLevels().get(2));

    TestPlan plan = new TestPlan(home);
    BufferedImage image = new BufferedImage(800, 1000, BufferedImage.TYPE_INT_ARGB);
    Graphics2D graphics = image.createGraphics();
    try {
      checkPages(home, plan, graphics);
      checkHiddenLevel(home, plan, graphics);
      checkRenderingFailures(home, plan, graphics);
    } finally {
      graphics.dispose();
    }
    System.out.println("Task 4 verification: PASS (" + checks + " checks)");
  }

  private static void checkPages(Home home, TestPlan plan, Graphics2D graphics) {
    Level selected = home.getSelectedLevel();
    for (int page = 0; page < home.getLevels().size(); page++) {
      check(plan.print(graphics, pageFormat(), page) == Printable.PAGE_EXISTS,
          "plan page exists: " + page);
      check(plan.printedLevel == home.getLevels().get(page), "plans follow home level order");
      check(home.getSelectedLevel() == selected, "printing restores the selected level");
    }
    check(plan.print(graphics, pageFormat(), 3) == Printable.NO_SUCH_PAGE
        && home.getSelectedLevel() == selected, "a missing page preserves the selected level");
  }

  private static void checkHiddenLevel(Home home, TestPlan plan, Graphics2D graphics) {
    Level selected = home.getSelectedLevel();
    Level hidden = home.getLevels().get(1);
    hidden.setViewable(false);
    try {
      check(plan.print(graphics, pageFormat(), 1) == Printable.PAGE_EXISTS,
          "the next viewable level has a page");
      check(plan.printedLevel == home.getLevels().get(2), "printing skips unviewable levels");
      check(home.getSelectedLevel() == selected, "skipping a level preserves selection");
      check(plan.print(graphics, pageFormat(), 2) == Printable.NO_SUCH_PAGE
          && home.getSelectedLevel() == selected, "unviewable levels do not add pages");
    } finally {
      hidden.setViewable(true);
    }
  }

  private static void checkRenderingFailures(Home home, TestPlan plan, Graphics2D graphics) {
    Level originalSelection = home.getSelectedLevel();
    plan.fail = true;
    try {
      for (Level selected : home.getLevels()) {
        home.setSelectedLevel(selected);
        for (int page = 0; page < home.getLevels().size(); page++) {
          plan.printedLevel = null;
          try {
            plan.print(graphics, pageFormat(), page);
            throw new AssertionError("Expected rendering failure");
          } catch (IllegalStateException failure) {
            check("Test rendering failure".equals(failure.getMessage())
                && plan.printedLevel == home.getLevels().get(page)
                && home.getSelectedLevel() == selected,
                "a rendering failure restores selected " + selected.getName());
          }
        }
      }
    } finally {
      plan.fail = false;
      home.setSelectedLevel(originalSelection);
    }
  }

  private static PageFormat pageFormat() {
    Paper paper = new Paper();
    paper.setSize(612, 792);
    paper.setImageableArea(36, 36, 540, 720);
    PageFormat format = new PageFormat();
    format.setPaper(paper);
    return format;
  }

  private static void check(boolean passed, String message) {
    if (!passed) {
      throw new AssertionError(message);
    }
    checks++;
    System.out.println("PASS: " + message);
  }
}

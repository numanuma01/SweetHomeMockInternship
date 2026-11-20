import com.eteks.sweethome3d.io.*;
import com.eteks.sweethome3d.model.*;
import com.eteks.sweethome3d.swing.*;
import com.eteks.sweethome3d.viewcontroller.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.print.*;
import java.io.*;
import java.util.*;
import java.util.List;
import javax.swing.*;
import javax.swing.table.*;

/**
 * Standalone Swing integration checks; requires Java 8 and a graphical desktop.
 * Run from the repository root after mvn compile:
 * javac -cp 'target/classes:SweetHome3D/lib/*' -d /tmp/task4-verification verification/Task4Verification.java
 * java -cp 'target/classes:SweetHome3D/lib/*:/tmp/task4-verification' Task4Verification
 *
 * Generates submissions/task4-all-levels.pdf through HomePDFPrinter. The probe
 * subclasses record real print state and inject failures; they delegate rendering.
 * The sample's saved group-expansion listener is invoked synchronously on the EDT.
 * Header/content assertions use a separate in-memory PDF with extractable text.
 */
public class Task4Verification {
  static int checks;
  static DefaultUserPreferences preferences;
  static Font font = new Font("SansSerif", Font.PLAIN, 12);
  static class ProbePlan extends PlanComponent {
    final Home home;
    boolean printing;
    boolean fail;
    final List<Level> rendered = new ArrayList<>();
    ProbePlan(Home home, UserPreferences prefs, PlanController controller) { super(home, prefs, controller); this.home = home; }
    @Override public int print(Graphics g, PageFormat f, int page) {
      printing=true;
      try { return super.print(g,f,page); } finally { printing=false; }
    }
    @Override protected List<Selectable> getPaintedItems() {
      if (home != null && printing) {
        rendered.add(home.getSelectedLevel());
        if (fail) throw new IllegalStateException("Injected plan rendering failure");
      }
      return super.getPaintedItems();
    }
  }
  static class ProbeTable extends FurnitureTable {
    boolean fail;
    List<HomePieceOfFurniture> printedRows;
    List<Object> printedColumns;
    ProbeTable(Home home, UserPreferences prefs, FurnitureController controller) { super(home, prefs, controller); }
    @Override public Printable getPrintable(JTable.PrintMode mode, java.text.MessageFormat header, java.text.MessageFormat footer) {
      printedRows = new ArrayList<>();
      printedColumns = new ArrayList<>();
      for (int row=0; row<getRowCount(); row++) printedRows.add((HomePieceOfFurniture)getValueAt(row,0));
      for (int col=0; col<getColumnCount(); col++) printedColumns.add(getColumnModel().getColumn(col).getIdentifier());
      final Printable delegate = super.getPrintable(mode, header, footer);
      return (g, format, index) -> {
        if (fail) throw new PrinterException("Injected table printing failure");
        return delegate.print(g, format, index);
      };
    }
  }
  static class ProbeFactory extends SwingViewFactory {
    ProbeTable table;
    ProbePlan plan;
    @Override public View createFurnitureView(Home h, UserPreferences p, FurnitureController c) { return table = new ProbeTable(h,p,c); }
    @Override public PlanView createPlanView(final Home h, UserPreferences p, PlanController c) {
      return new MultipleLevelsPlanPanel(h,p,c) {
        @Override protected PlanComponent createPlanComponent(Home h2, UserPreferences p2, PlanController c2) { return plan = new ProbePlan(h2,p2,c2); }
      };
    }
  }
  public static void main(String[] args) throws Exception {
    System.setProperty("com.eteks.sweethome3d.no3D", "true");
    Locale.setDefault(Locale.ENGLISH);
    try {
      SwingUtilities.invokeAndWait(() -> {
        try {
          UIManager.setLookAndFeel("javax.swing.plaf.metal.MetalLookAndFeel");
          preferences = new DefaultUserPreferences();
          preferences.setLanguage("en");
          preferences.setUnit(LengthUnit.CENTIMETER);
          verifyPlan();
          verifyCoordinator(args.length == 0 ? "submissions/task4-all-levels.pdf" : args[0]);
        } catch (Exception ex) { throw new RuntimeException(ex); }
      });
      verifyWorker();
      System.out.println("Task 4 integration: PASS ("+checks+" checks)");
      System.exit(0);
    } catch(Throwable ex) { ex.printStackTrace(); System.exit(1); }
  }
  static void check(boolean passed, String message) { if(!passed) throw new AssertionError(message); checks++; System.out.println("PASS: "+message); }
  static PageFormat page() {
    Paper p=new Paper(); p.setSize(612,792); p.setImageableArea(36,36,540,720);
    PageFormat f=new PageFormat(); f.setPaper(p); return f;
  }
  static Graphics2D graphics() { return new BufferedImage(612,792,BufferedImage.TYPE_INT_RGB).createGraphics(); }
  static HomePrint settings(boolean furniture, boolean plan, Float scale) { return new HomePrint(HomePrint.PaperOrientation.PORTRAIT,612,792,36,36,36,36,furniture,plan,false,scale,null,null); }
  static void verifyPlan() {
    Home h=new Home(); h.getCompass().setVisible(false);
    Level high=new Level("Upper",500,10,240), low=new Level("Ground",0,10,240), same=new Level("Mezzanine",0,10,240), hidden=new Level("Hidden",250,10,240);
    h.addLevel(high); h.addLevel(low); h.addLevel(hidden); h.addLevel(same);
    hidden.setViewable(false); high.setVisible(false);
    for(Level level:h.getLevels()) { Wall w=new Wall(0,0,200+level.getElevation(),0,10,240); h.addWall(w); w.setLevel(level); }
    h.setSelectedLevel(hidden);
    ProbePlan plan=new ProbePlan(h,preferences,null);
    Graphics2D g=graphics();
    try {
      List<Level> expected=new ArrayList<>(); for(Level l:h.getLevels()) if(l.isViewable()) expected.add(l);
      check(expected.equals(Arrays.asList(low,same,high)),"home order includes equal-elevation levels and viewable levels with visible=false");
      for(int i=0;i<expected.size();i++) {
        plan.rendered.clear();
        check(plan.print(g,page(),i)==Printable.PAGE_EXISTS,"plan page exists: "+expected.get(i).getName());
        check(!plan.rendered.isEmpty() && new HashSet<>(plan.rendered).equals(Collections.singleton(expected.get(i))),"renders the correct level at page "+i);
        check(h.getSelectedLevel()==hidden,"restores original unviewable selected level after page "+i);
      }
      check(plan.print(g,page(),3)==Printable.NO_SUCH_PAGE && h.getSelectedLevel()==hidden,"out-of-range page preserves selection");
      check(plan.print(g,page(),-1)==Printable.NO_SUCH_PAGE && h.getSelectedLevel()==hidden,"negative page preserves selection");
      plan.fail=true;
      try {
        for(Level selected:expected) {
          h.setSelectedLevel(selected);
          for(int pageIndex=0;pageIndex<expected.size();pageIndex++) {
            plan.rendered.clear();
            try {
              plan.print(g,page(),pageIndex);
              throw new AssertionError("Expected rendering failure");
            } catch(IllegalStateException expectedFailure) {
              check(h.getSelectedLevel()==selected && plan.rendered.equals(Collections.singletonList(expected.get(pageIndex))),
                  "render failure on " + expected.get(pageIndex).getName() + " restores selected " + selected.getName());
            }
          }
        }
      } finally {
        plan.fail=false;
        h.setSelectedLevel(hidden);
      }
      h.setSelectedLevel(null);
      check(plan.print(g,page(),1)==Printable.PAGE_EXISTS && h.getSelectedLevel()==null,"null selected level restored");
      h.setPrint(settings(false,true,1f));
      check(plan.print(g,page(),2)==Printable.PAGE_EXISTS && plan.print(g,page(),3)==Printable.NO_SUCH_PAGE,"explicit scale still yields exactly one fitted page per viewable level");
      for(Level level:h.getLevels()) level.setViewable(false);
      check(plan.print(g,page(),0)==Printable.NO_SUCH_PAGE && h.getSelectedLevel()==null,"all hidden levels produce no plan pages");
      Home empty=new Home(); empty.getCompass().setVisible(false); empty.addLevel(new Level("Empty",0,10,240));
      ProbePlan emptyPlan=new ProbePlan(empty,preferences,null);
      check(emptyPlan.print(g,page(),0)==Printable.PAGE_EXISTS && emptyPlan.print(g,page(),1)==Printable.NO_SUCH_PAGE,"empty viewable level has one blank page");
      Home flat=new Home(); flat.getCompass().setVisible(false); flat.addWall(new Wall(0,0,1000,0,10,240));
      ProbePlan flatPlan=new ProbePlan(flat,preferences,null);
      check(flatPlan.print(g,page(),0)==Printable.PAGE_EXISTS && flatPlan.print(g,page(),1)==Printable.NO_SUCH_PAGE,"home without levels keeps normal fitted printing");
      flat.setPrint(settings(false,true,1f));
      check(flatPlan.print(g,page(),1)==Printable.PAGE_EXISTS,"home without levels retains explicit-scale tiling");
    } finally { g.dispose(); }
  }
  static List<Object> columns(JTable t) { List<Object> c=new ArrayList<>(); for(int i=0;i<t.getColumnCount();i++) c.add(t.getColumnModel().getColumn(i).getIdentifier()); return c; }
  static List<HomePieceOfFurniture> rows(JTable t) { List<HomePieceOfFurniture> r=new ArrayList<>(); for(int i=0;i<t.getRowCount();i++) r.add((HomePieceOfFurniture)t.getValueAt(i,0)); return r; }
  static void verifyCoordinator(String path) throws Exception {
    Home h;
    try(DefaultHomeInputStream in=new DefaultHomeInputStream(new FileInputStream("samples/MultiLevelHome.sh3d"))) { h=in.readHome(); }
    h.setName("MultiLevelHome.sh3d");
    h.setPrint(settings(true,true,null));
    ProbeFactory factory=new ProbeFactory();
    HomeController controller=new HomeController(h,preferences,factory,new FileContentManager(preferences));
    HomePane pane=(HomePane)controller.getView();
    JFrame frame=new JFrame("Task 4 printing verification"); frame.setContentPane(pane); frame.setSize(1100,800); frame.setVisible(true);
    try {
      ProbeTable table=factory.table;
      javax.swing.event.AncestorEvent added=new javax.swing.event.AncestorEvent(table,javax.swing.event.AncestorEvent.ANCESTOR_ADDED,table.getParent(),table.getParent());
      for(javax.swing.event.AncestorListener listener:table.getAncestorListeners()) listener.ancestorAdded(added);
      List<HomePieceOfFurniture> expected=rows(table);
      System.out.println("SAMPLE ROWS: "+expected.size()); for(HomePieceOfFurniture p:expected) System.out.println("  "+p.getName()+" / "+p.getLevel().getName());
      check(expected.size()==5,"sample expanded group includes all five furniture rows");
      List<HomePieceOfFurniture.SortableProperty> properties=new ArrayList<>(h.getFurnitureVisibleProperties());
      List<Object> screenColumns=columns(table);
      TableColumnModel screenModel=table.getColumnModel();
      Color grid=table.getGridColor();
      FurnitureTable.FurnitureFilter filter=table.getFurnitureFilter();
      Level selected=h.getSelectedLevel();
      HomePrintableComponent printable=new HomePrintableComponent(h,controller,font);
      check(printable.getPageCount()==4,"sample full printing path counts one table and three plan pages");
      check(h.getSelectedLevel()==selected && h.getFurnitureVisibleProperties().equals(properties) && table.getColumnModel()==screenModel && columns(table).equals(screenColumns),"page counting restores level and exact screen columns");
      Graphics2D g=graphics();
      try {
        check(printable.print(g,page(),0)==Printable.PAGE_EXISTS,"sample furniture page exists");
        check(table.printedRows.equals(expected),"printed furniture includes all expected rows from all levels");
        check(table.printedColumns.size()==screenColumns.size()+1 && table.printedColumns.get(table.printedColumns.size()-1)==HomePieceOfFurniture.SortableProperty.LEVEL,"print-only Level column appended exactly once");
        for(int i=0;i<3;i++) {
          factory.plan.rendered.clear();
          check(printable.print(g,page(),i+1)==Printable.PAGE_EXISTS,"coordinator plan page "+i+" exists");
          check(new HashSet<>(factory.plan.rendered).equals(Collections.singleton(h.getLevels().get(i))),"coordinator prints home level order at plan "+i);
          check(h.getSelectedLevel()==selected,"coordinator restores selection after plan "+i);
        }
        check(printable.print(g,page(),4)==Printable.NO_SUCH_PAGE && h.getSelectedLevel()==selected,"coordinator missing page preserves level");
        table.fail=true;
        try { printable.print(g,page(),0); throw new AssertionError("Expected furniture print failure"); } catch(PrinterException injected) { check(table.getColumnModel()==screenModel && table.getGridColor().equals(grid) && h.getFurnitureVisibleProperties().equals(properties) && table.getFurnitureFilter()==filter,"furniture print failure restores column model/grid/filter/properties"); }
        table.fail=false;
        factory.plan.fail=true;
        try { printable.print(g,page(),1); throw new AssertionError("Expected plan print failure"); } catch(IllegalStateException injected) { check(h.getSelectedLevel()==selected && table.getColumnModel()==screenModel,"coordinator plan failure restores level and columns"); }
        factory.plan.fail=false;
        for(boolean show:new boolean[]{true,false}) {
          List<HomePieceOfFurniture.SortableProperty> cols=new ArrayList<>(properties); if(show) cols.add(HomePieceOfFurniture.SortableProperty.LEVEL); h.setFurnitureVisibleProperties(cols);
          TableColumnModel before=table.getColumnModel();
          table.print(g,page(),0);
          check(Collections.frequency(table.printedColumns,HomePieceOfFurniture.SortableProperty.LEVEL)==1 && h.getFurnitureVisibleProperties().equals(cols) && table.getColumnModel()==before && columns(table).equals(new ArrayList<Object>(cols)),"Level "+(show?"shown":"hidden")+" on screen is preserved without duplicate printed columns");
        }
        Level hidden=h.getLevels().get(1); hidden.setViewable(false);
        printable=new HomePrintableComponent(h,controller,font);
        check(printable.getPageCount()==3,"unviewable sample level omitted from page count");
        printable.print(g,page(),0);
        check(table.printedRows.size()==4 && table.printedRows.stream().noneMatch(p->p.getLevel()==hidden),"furniture on unviewable level excluded");
        hidden.setViewable(true);
        h.setPrint(settings(true,false,null));
        printable=new HomePrintableComponent(h,controller,font);
        check(printable.getPageCount()==1 && table.printedRows.equals(expected),"furniture-only printing covers every viewable level");
        h.setPrint(settings(false,true,null));
        check(new HomePrintableComponent(h,controller,font).getPageCount()==3,"plan-only printing covers three viewable levels");
      } finally { g.dispose(); }
      verifyHeaders(h,controller);
      h.setPrint(settings(true,true,null)); h.setFurnitureVisibleProperties(properties); h.setSelectedLevel(selected);
      try(OutputStream out=new FileOutputStream(path)) { new HomePDFPrinter(h,preferences,controller,font).write(out); }
      check(h.getSelectedLevel()==selected && h.getFurnitureVisibleProperties().equals(properties) && table.getColumnModel()==screenModel && columns(table).equals(screenColumns) && table.getFurnitureFilter()==filter,"PDF export preserves selected level, exact columns, and furniture filter");
      System.out.println("APPLICATION PDF: "+new File(path).getAbsolutePath());
    } finally { frame.dispose(); }
  }
  static void verifyHeaders(Home h, HomeController controller) throws Exception {
    h.setPrint(new HomePrint(HomePrint.PaperOrientation.PORTRAIT,612,792,36,36,36,36,true,true,false,1f,
        "$level | $pageNumber/$pageCount | $planScale",null));
    HomePrintableComponent printable=new HomePrintableComponent(h,controller,font);
    ByteArrayOutputStream data=new ByteArrayOutputStream();
    com.lowagie.text.Document doc=new com.lowagie.text.Document(new com.lowagie.text.Rectangle(612,792));
    com.lowagie.text.pdf.PdfWriter writer=com.lowagie.text.pdf.PdfWriter.getInstance(doc,data);
    doc.open();
    for(int page=0;page<printable.getPageCount();page++) {
      Graphics2D g=writer.getDirectContent().createGraphics(612,792);
      try { printable.print(g,page(),page); } finally { g.dispose(); }
      if(page+1<printable.getPageCount()) doc.newPage();
    }
    doc.close();
    com.lowagie.text.pdf.PdfReader reader=new com.lowagie.text.pdf.PdfReader(data.toByteArray());
    try {
      com.lowagie.text.pdf.parser.PdfTextExtractor extractor=new com.lowagie.text.pdf.parser.PdfTextExtractor(reader);
      for(int page=2;page<=4;page++) {
        String text=extractor.getTextFromPage(page);
        check(text.contains("Level "+(page-2)+" | "+page+"/4 | 1/") && !text.contains("| 1/1"),"plan header identifies correct level, page number/count, and fitted scale at PDF page "+page);
      }
      String tableText=extractor.getTextFromPage(1);
      check(tableText.contains("Fridge & Freezer") && tableText.contains("Bunk bed") && tableText.contains("Computer workstation") && tableText.contains("Armchair") && tableText.contains("Level 0") && tableText.contains("Level 1") && tableText.contains("Level 2"),"rendered PDF text contains furniture from all three levels");
    } finally { reader.close(); }
  }
  static void verifyWorker() throws Exception {
    final Home[] h=new Home[1]; final HomeController[] c=new HomeController[1]; final JFrame[] f=new JFrame[1]; final ProbeFactory factory=new ProbeFactory();
    SwingUtilities.invokeAndWait(() -> {
      try {
        try(DefaultHomeInputStream in=new DefaultHomeInputStream(new FileInputStream("samples/MultiLevelHome.sh3d"))) { h[0]=in.readHome(); }
        h[0].setPrint(settings(true,true,null));
        c[0]=new HomeController(h[0],preferences,factory,new FileContentManager(preferences));
        f[0]=new JFrame("Background print verification"); f[0].setContentPane((HomePane)c[0].getView()); f[0].setSize(1000,700); f[0].setVisible(true);
      } catch(Exception ex) { throw new RuntimeException(ex); }
    });
    Level selected=h[0].getSelectedLevel();
    List<Object> before=columns(factory.table);
    HomePrintableComponent printable=new HomePrintableComponent(h[0],c[0],font);
    Graphics2D g=graphics();
    try {
      check(printable.getPageCount()==4 && h[0].getSelectedLevel()==selected && columns(factory.table).equals(before),"background printer page counting preserves selection and screen columns");
      factory.table.fail=true;
      try { printable.print(g,page(),0); throw new AssertionError("Expected failure"); } catch(PrinterException expected) { check(columns(factory.table).equals(before),"background furniture failure restores screen columns"); }
      factory.table.fail=false; factory.plan.fail=true;
      try { printable.print(g,page(),1); throw new AssertionError("Expected failure"); } catch(IllegalStateException expected) { check(h[0].getSelectedLevel()==selected,"background plan failure restores selected level"); }
      factory.plan.fail=false;
    } finally { g.dispose(); SwingUtilities.invokeAndWait(() -> f[0].dispose()); }
  }

}

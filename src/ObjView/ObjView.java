package ObjView;



// ObjView.java
// Andrew Davison, April 2007, ad@fivedots.coe.psu.ac.th

/* Load, display and examine a Wavefront file (OBJ file).

   The examination generates an examObj.txt file which
   contains the arrays holding the model's
   vertices, normals, texture coordinates,
   and strip lengths, and other variables for its material
   and/or texture information.

   These methods can be pasted into the MIDP OBJShape.java
   class to allow the model to be displayed in the ViewerES
   MIDP/JOGL-ES application.
*/

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;


public class ObjView// extends JFrame
{

  public ObjView(String fnm) 
  {
    //super("ObjView");
    //Container c = getContentPane();
    //c.setLayout( new BorderLayout() );
    WrapObjView w3d = new WrapObjView(fnm);     
                               // panel holding the 3D canvas
    //c.add(w3d, BorderLayout.CENTER);
    
    //setDefaultCloseOperation( JFrame.EXIT_ON_CLOSE );
    //pack();
    //setResizable(false);    // fixed size display
    //setVisible(true);
  } // end of ObjView()


// -----------------------------------------

  /*public static void main(String[] args)
  { 
	if (args.length != 1) {
      System.out.println("Usage: java ObjView <.obj fnm>");
      System.exit(1);
    }
    new ObjView(args[0]);
  }*/

} // end of ObjView class

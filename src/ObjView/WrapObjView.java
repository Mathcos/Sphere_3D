package ObjView;



// WrapObjView.java
// Andrew Davison, April 2007, ad@fivedots.coe.psu.ac.th

/* Load a model stored in a Wavefront file (OBJ file).
   The model is triangulated and stripified as it is loaded. 
   The loaded model is displayed on-screen, and can be translated
   and rotated by using the mouse.

   The OBJ file is translated into a BranchGroup with perhaps many
   Shape3D children. Each Shape3D child has a single Geometry node and
   Appearance node.

   Because of the triangulation and stripification, the Geometry is
   a TriangleStripArray node, and its points are interleaved.
   Each 'point' is a tuple of values:
       textures coords, colour coords, normals, vertices

   A texture coord may have 2, 3, or 4 components.
   A colour coord may have 3 or 4 components.
   A normal has 3 components. A vertex has 3 components.

   An OBJ file in Java 3D can't store colour coordinates,
   but this code still checks for them.

   All the OBJ files with textures that I've seen only use 2D 
   texture coordinates (i.e. (s,t) values). Also, only 2D texture
   coordinates are understood by JOGL-ES.

   Some OBJ files have material data (in a .mtl file),
   which is stored as colour and shininess values in the shape's 
   Appearance node.

   Java 3D understands a large set of usemtl names (e.g. flesh)
   without requiring a mtllib call in the OBJ file. You can use this
   to quickly change the colour of a model, by adding a usemtl line
   to the file (e.g. usemtl flesh). The predefined names are listed 
   in Java 3D's ObjectFile documentation.

   OvjView writes output to the screen, reporting what it has found, and
   generates multiple static final variables for each Shape3D it 
   detects in the model. 

   These variables are written to EXAMINE_FN (examObj.txt). The
   programmer can then manually paste them into 
   OBJShape.java used by the ViewerES MIDP/JOGL-ES application.

   The data falls into 3 groups: model transformations, model
   coordinates, and material settings.

   // 1. model transformations
   private static final float xCenter, yCenter, zCenter;   
     // the center of the model; used to move the model to the origin

   private static final float scaleFactor;
     // used to scale the model so its longest dimension is 1 unit in length


   // 2. model coordinates

   private static final byte[] verts = { ... }
     // vertices for the model, scaled to be between -128-127, so each
        one will fit into a byte.
        The vertices will form triangle strips when rendered.

   private static final boolean hasNormals;   // true or false
   private static final byte[] normals = { ... }
     // normals for the model, scaled to be between -128-127, so each
        one will fit into a byte. This array may have no values, which
        means the model doesn't have normals. In that case, hasNormals
        will be false.

   private static final boolean hasTexture;   // true or false
   private static final float[] texCoords; = { ... }
     // tex coords for the model, in the form of floats between 0 and 1.0f.
        This array may have no values, which means the model doesn't use
        texturing. In that case, hasTexture will be false.

     // OpenGL ES can only deal with 2D tex coords, although it is 
        possibly for ObjView to output 3D and 4D coordinates. But I've never
        seen an OBJ model that uses them.

   private static final int[] strips = {...}
     // the number of vertices in each triangle strip making up
        the model

   Note that there is no colour[] array since Java 3D's ObjFile class
   doesn't support colour coordinates, 
   so there's no way that ObjView can generate them.


   // 3. material settings

   private static final String TEX_FNM = "/...";
      // the texture filename. This should be placed in the res/
         directory if you're using WTK.
         This string will always be the name of the OBJ file, but
         with a ".png" extension. If that's wrong, then **YOU** will 
         have to edit it.

   private static final float[] ambientMat, emissiveMat, diffuseMat, specularMat;
     // the RGBA values for the ambient, emissive, diffuse, and
        specular properties of the material. They will always have values.

   private static final float shininess;   // the shinness value (ranging from 0 to 128)
       // it will always have a value

   This approach means that a shape can only utilize at most one
   material and texture.

   ---------
   The main drawback with this approach is the generation of
   very large arrays, which may be too big for the WTK to compile.
   The MIDP emulator only allows an array to be at most 
   32 Kb large.

   OBJShape is only designed to render one shape, so if variables for
   multiple shapes are output then multiple OBJShape instances will be
   needed. Also, each shape is scaled and repositioned at the
   origin. The scaling for the different shapes will usually be different
   since it depends on the largest dimension of the shape.
*/


import javax.swing.*;

import java.awt.*;
import java.util.*;
import java.io.*;
import java.net.*;

import com.sun.j3d.utils.universe.*;
import com.sun.j3d.utils.geometry.*;

import javax.media.j3d.*;
import javax.vecmath.*;

import com.sun.j3d.utils.behaviors.vp.*;
import com.sun.j3d.loaders.*;
import com.sun.j3d.loaders.*;
import com.sun.j3d.loaders.objectfile.ObjectFile;



public class WrapObjView extends JPanel
{
  private static final int PWIDTH = 512;   // size of panel
  private static final int PHEIGHT = 512; 

  private static final int BOUNDSIZE = 100;  // larger than world

  private static final Point3d USERPOSN = new Point3d(0,0,5);
    // initial user position

  private static final String MODELS_DIR = "./bin/ObjView/models/";  //location of OBJ files
  private static final String EXAMINE_FN = "examObj.txt";
    // the file where the model methods are written

  // private static final double CREASE_ANGLE = 60.0;

  private static final double MAX_ELEMS = 4000;
      /* (Probable) max number of elements in a coords array before 
         the MIDP 32kb byte[] array limit is reached. Needs testing. */


  private SimpleUniverse su;
  private BranchGroup sceneBG;
  private BoundingSphere bounds;   // for environment nodes

  private FileWriter ofw;     // for writing out model info
  private String modelNm;     // name before the ".obj" extension

  // vertex format information
  private boolean isInterleaved;
  private boolean hasNormals;
  private int numColours;
  private int numTextures;
  private int numVertElems;
  private int pointsCount;
 


  public WrapObjView(){};
  public WrapObjView(String fnm)
  // A panel holding a 3D canvas
  {
    setLayout( new BorderLayout() );
    setOpaque( false );
    setPreferredSize( new Dimension(PWIDTH, PHEIGHT));

    GraphicsConfiguration config =
					SimpleUniverse.getPreferredConfiguration();
    Canvas3D canvas3D = new Canvas3D(config);
    add("Center", canvas3D);
    canvas3D.setFocusable(true);     // give focus to the canvas 
    canvas3D.requestFocus();

    modelNm = fnm.substring(0, fnm.lastIndexOf("."));

    su = new SimpleUniverse(canvas3D);

    createSceneGraph(fnm);
    initUserPosition();        // set user's viewpoint
    orbitControls(canvas3D);   // controls for moving the viewpoint
    
    su.addBranchGraph( sceneBG );
  } // end of WrapObjView()



  private void createSceneGraph(String fnm) 
  // initilise the scene
  { 
    sceneBG = new BranchGroup();
    bounds = new BoundingSphere(new Point3d(0,0,0), BOUNDSIZE);   

    lightScene();         // add the lights
    addBackground();      // add the sky

    // load, examine, and display the model
    BranchGroup modelBG = loadModel(fnm);
    printGraphInfo(modelBG, fnm);
    sceneBG.addChild(modelBG);

    sceneBG.compile();   // fix the scene
  } // end of createSceneGraph()


  private void lightScene()
  /* One ambient light, 2 directional lights */
  {
    Color3f white = new Color3f(1.0f, 1.0f, 1.0f);

    // Set up the ambient light
    AmbientLight ambientLightNode = new AmbientLight(white);
    ambientLightNode.setInfluencingBounds(bounds);
    sceneBG.addChild(ambientLightNode);

    // Set up the directional lights
    Vector3f light1Direction  = new Vector3f(-1.0f, -1.0f, -1.0f);
       // left, down, backwards 
    Vector3f light2Direction  = new Vector3f(1.0f, -1.0f, 1.0f);
       // right, down, forwards

    DirectionalLight light1 = 
            new DirectionalLight(white, light1Direction);
    light1.setInfluencingBounds(bounds);
    sceneBG.addChild(light1);

    DirectionalLight light2 = 
        new DirectionalLight(white, light2Direction);
    light2.setInfluencingBounds(bounds);
    sceneBG.addChild(light2);
  }  // end of lightScene()



  private void addBackground()
  // A blue sky
  { Background back = new Background();
    back.setApplicationBounds( bounds );
   //back.setColor(0.17f, 0.65f, 0.92f);    // sky colour
   back.setColor(1.43f, 1.88f, 1.43f);  
    
    sceneBG.addChild( back );
  }  // end of addBackground()



  private void orbitControls(Canvas3D c)
  /* OrbitBehaviour allows the user to rotate around the scene, and to
     zoom in and out.  */
  {
    OrbitBehavior orbit = 
		new OrbitBehavior(c, OrbitBehavior.REVERSE_ALL);
    orbit.setSchedulingBounds(bounds);

    ViewingPlatform vp = su.getViewingPlatform();
    vp.setViewPlatformBehavior(orbit);	 
  }  // end of orbitControls()



  private void initUserPosition()
  // Set the user's initial viewpoint using lookAt()
  {
    ViewingPlatform vp = su.getViewingPlatform();
    TransformGroup steerTG = vp.getViewPlatformTransform();

    Transform3D t3d = new Transform3D();
    steerTG.getTransform(t3d);

    // args are: viewer posn, where looking, up direction
    t3d.lookAt( USERPOSN, new Point3d(0,0,0), new Vector3d(0,1,0));
    t3d.invert();

    steerTG.setTransform(t3d);
  }  // end of initUserPosition()


  private BranchGroup loadModel(String modelFnm)
  // load the OBJ model stored in modelFnm
  {
	String fnm = MODELS_DIR + modelFnm;
	//String fnm = "./bin/ObjView/models/" + modelFnm;
    System.out.println("Loading OBJ model from " + fnm);
    
    File file = new java.io.File(fnm);
    if (!file.exists()) {
    	System.out.println("Could not find " + fnm);
      
      /*String files;
      File folder = new File("./bin/ObjView/models");
      File[] listOfFiles = folder.listFiles(); 
     
      for (int i = 0; i < listOfFiles.length; i++)
      {
     
       if (listOfFiles[i].isFile()) 
       {
       files = listOfFiles[i].getName();
       System.out.println(files);
          }
       if(listOfFiles[i].isDirectory()){
    	   	files = listOfFiles[i].getName();
      		System.out.println("D::"+files);
  		}
      }*/
    
      
      return null;
    }

    /* Convert the filename to a URL, so the OBJ file can 
       find MTL and image files in the MODELS_DIR subdirectory
       at runtime. */
    URL url = null;
    try {
      url = file.toURI().toURL();
    }
    catch(Exception e) {
      System.out.println(e);
      return null;
    }

    // read in the geometry from the file
    // the loaded model is triangulated and stripified
	int flags = ObjectFile.TRIANGULATE | ObjectFile.STRIPIFY;
	ObjectFile f = new ObjectFile(flags);
	                    // (float)(CREASE_ANGLE * Math.PI/180.0));
    Scene scene = null;
    try {
      scene = f.load(url);
    }
	catch (FileNotFoundException e) {
	  System.out.println("Could not find " + fnm);
	  System.exit(1);
	}
	catch (ParsingErrorException e) {
	  System.out.println("Could not parse the contents of " + fnm);
	  System.out.println(e);
	  System.exit(1);
	}
	catch (IncorrectFormatException e) {
	  System.out.println("Incorrect format in " + fnm);
	  System.out.println(e);
	  System.exit(1);
	}

    // return the model's BranchGroup
    if(scene != null)
      return scene.getSceneGroup();
    else
      return null;
  }  // end of loadModel()


  // ---------------------- examine the loaded model -----------------


  private void printGraphInfo(BranchGroup bg, String fnm)
  /* Traverse the model's scene graph and store 
     information in EXAMINE_FN via the ofw FileWriter.
  */
  {
    System.out.println("Writing " + fnm + " model details to " + EXAMINE_FN);
    try {
      ofw = new FileWriter( EXAMINE_FN );
      fileWrite("\n  // Arrays for model in " + fnm  + "\n\n");
      examineNode(bg);
      ofw.close();
    }
    catch( IOException ioe )
    { System.err.println("Cannot write to " + EXAMINE_FN); }
  }  // end of printGraphInfo()


  private void examineNode(Node node)
  /* A Node can be a Group or a Leaf.
     Recursively call examineNode() on the children of a Group.
  */
  {
    if(node instanceof Group) {     // the Node is a Group
      Group g = (Group) node;
      if(g instanceof TransformGroup) {    // consider subclass
        Transform3D t3d = new Transform3D();
	    ((TransformGroup) g).getTransform(t3d);
        System.out.println(t3d.toString());   // show Transform3D info for TG
      }

      boolean hasMultipleShapes = false;
      if (g.numChildren() > 1) {
        System.out.println(g.numChildren() + " children");
        fileWrite("  // WARNING: created " + g.numChildren() + 
                                      " copies of all arrays\n\n");
        hasMultipleShapes = true;
      }

      Enumeration enumKids = g.getAllChildren();
      int shapeCount = 1;
      while(enumKids.hasMoreElements())  {   // visit Group children
        if (hasMultipleShapes) {
          fileWrite("  // ============================ shape group " + shapeCount + 
                    " =======================\n\n");
          System.out.println("================================");
          System.out.println("Child " + shapeCount);
          System.out.println("");
        }
        examineNode((Node) enumKids.nextElement());
        shapeCount++;
      }
    }
    else if (node instanceof Leaf) {     // the Node is a Leaf
      // System.out.println("Leaf: " + node.getClass());
      if (node instanceof Shape3D)
        examineShape3D((Shape3D) node);
    }
    // else    // the Node is something other than a Group or Leaf
      // System.out.println("Node: " + node.getClass());

  }  // end of examineNode()


  private void examineShape3D(Shape3D shape)
  /* A Shape3D is a container for Geometry and Appearance nodes.
     A shape may contain many geometries: examine each one with
     examineGeometry().
     Report appearance info with printAppearance().
  */
  {
    int numGeoms = shape.numGeometries();    // consider geometries
    if (numGeoms == 0)
      System.out.println("No Geometry Components");
    else if (numGeoms == 1) {
      Geometry g = shape.getGeometry();
      examineGeometry(1, g);
    }
    else {   // more than one geometry in the shape
      System.out.println("Num. of Geometries: " + numGeoms);
      fileWrite("// WARNING: created " + numGeoms + 
                                 " copies of all geometry methods\n\n");

      Enumeration enumGeoms = shape.getAllGeometries();
      int i = 1;
      while(enumGeoms.hasMoreElements()) {
        fileWrite("// ======================geometry array group " + i + 
                    " =======================\n\n");
        examineGeometry(i, (Geometry) enumGeoms.nextElement() );
        i++;
      }
    }
    System.out.println("");

    Appearance app = shape.getAppearance();    // consider appearance
    if (app == null)
      System.out.println("No Appearance Component");
    else
      printAppearance(app);
  }  // end of examineShape3D()



  // -------------------- report on geometry ----------

  private void examineGeometry(int index, Geometry geo)
  /* Due to triangulation and stripification, the geometry 
     will be a TriangleStripArray. Report on its interleaved
     coordinates, and the strip lengths. 
  */
  {
    System.out.println("Geometry: " + geo.getClass());
    if(geo instanceof TriangleStripArray) {
      TriangleStripArray tsa = (TriangleStripArray) geo;

      examineVertexFormat(tsa);

      pointsCount = tsa.getVertexCount();
      System.out.println("Total num. of Points: " + pointsCount); 

      if (isInterleaved)
        extractCoords(tsa);

      stripInfo(tsa);
    }
  }  // end of examineGeometry()



  private void examineVertexFormat(TriangleStripArray tsa)
  // examine the details of the vertex format
  { 
    // reset vertex format globals
    isInterleaved = false;
    hasNormals = false;
    numColours = 0;
    numTextures = 0;
    numVertElems = 3;    // for (x,y,z) coord

    int vf = tsa.getVertexFormat();

    if ((vf & (GeometryArray.BY_REFERENCE)) != 0)
      System.out.println("VF: by reference");

    if ((vf & (GeometryArray.INTERLEAVED)) != 0) {
      isInterleaved = true;
      System.out.println("VF: interleaved");
    }
    else
      System.out.println("VF: **NOT** interleaved");

    // a colour coordinate may have 3 or 4 components
    if ((vf & GeometryArray.COLOR_3) != 0) {
      numColours = 3;
      System.out.println("VF: per vertex colours (3)");
    }
    else if ((vf & GeometryArray.COLOR_4) != 0) {
      numColours = 4;
      System.out.println("VF: per vertex colours (4)");
    }
    numVertElems += numColours;

    if ((vf & GeometryArray.NORMALS) != 0) {
      hasNormals = true;
      System.out.println("VF: per vertex normals");
      numVertElems += 3;
    }

    // a texture coordinate may have 2, 3 or 4 components
    if ((vf & GeometryArray.TEXTURE_COORDINATE_2) != 0) {
      numTextures = 2;
      System.out.println("VF: per vertex texture coords (2)");
    }
    else if ((vf & GeometryArray.TEXTURE_COORDINATE_3) != 0) {
      numTextures = 3;
      System.out.println("VF: per vertex texture coords (3)");
    }
    else if ((vf & GeometryArray.TEXTURE_COORDINATE_4) != 0) {
      numTextures = 4;
      System.out.println("VF: per vertex texture coords (4)");
    }
    numVertElems += numTextures;

    System.out.println("VF: num. of vertex elements: " + numVertElems);
  }  // end of examineVertexFormat()



  private void extractCoords(TriangleStripArray tsa)
  /* Report data for vertices, normals, texture coords, and colour coords.
     Each 'point' in the TriangleStrip array is a tuple of values:
       textures coords, colour coords, normals, vertices

     The number of textured coords and colour coords may vary. There's
     always 3 normals values, 3 vertex values.
  */
  {
    float[] itlvVerts = tsa.getInterleavedVertices();
    System.out.println("Num. of interleaved Verts: " + itlvVerts.length);

    int pointOffset = numTextures + numColours;
    if (hasNormals)
      pointOffset += 3;  // for the 3 normal values
    getCoords(itlvVerts, "verts", pointOffset, 3);    // (x,y,z)

    if (hasNormals) {
      fileWrite("  private static final boolean hasNormals = true;\n\n");
      int normOffset = numTextures + numColours;
      getCoords(itlvVerts, "normals", normOffset, 3);  // (x,y,z)
    }
    else {
      fileWrite("  private static final boolean hasNormals = false;\n");
      fileWrite("  private static final byte[] normals = {};   // not used\n\n");
    }

    if (numTextures != 0) {    // 2, 3, or 4 values
      fileWrite("  private static final boolean hasTexture = true;\n\n");
      getCoords(itlvVerts, "texCoords", 0, numTextures);
    }
    else {
      fileWrite("  private static final boolean hasTexture = false;\n");
      fileWrite("  private static final float[] texCoords = {};   // not used\n\n");
    }

    if (numColours != 0)     // 3 or 4 values
      getCoords(itlvVerts, "colors", numTextures, numColours);
  }  // end of extractCoords()


  private void getCoords(float[] iVerts, String coordType, 
                                        int offset, int numVals)
  /* Pull out the relevant coordinates from the TriangleStrip array,
     storing them in their own float array. Scale them up, converting
     them to integers. These are placed in an integer array, which becomes
     the values listed in the model methods.
  */
  {
    float[] coords = new float[pointsCount*numVals];   
    int posn = 0;

    // pull out the relevant numbers from the interleaved verts array
    for(int i=offset; i < iVerts.length; i += numVertElems) {
      for (int j=0; j < numVals; j++)
        coords[posn+j] = iVerts[i+j];
      posn += numVals;
    }

    // find the min and max values in the coords array
    float minVal = coords[0];
    float maxVal = coords[0];
    for(int i=1; i < coords.length; i++) {
      if (minVal > coords[i])
        minVal = coords[i];
      if (maxVal < coords[i])
        maxVal = coords[i];
    }
    System.out.println("");
    System.out.println(coordType + " coords min/max: " + minVal + " / " + maxVal);

    printShapeCoords(coords, coordType, minVal, maxVal, numVals);
  }  // end of getCoords()


  // -------------------- print the shape coordinates --------------------------


  private void printShapeCoords(float[] coords, String coordType, 
                               float minVal, float maxVal, int numVals)
  /* Convert the shape's vertices, normals, and color floats to integers 
     (by rescaling), and print them. Don't convert the tex coords. */
  {
    int[] icoords = new int[coords.length];   

    // vertices and normals are scaled to integers in the range -128 to 127
    if ( coordType.equals("verts") || coordType.equals("normals") ) {
      System.out.println("Rescale " + coordType + " coords to [-128 to 127]");
      for(int i=0; i < coords.length; i ++) {
        icoords[i] = (int) Math.round( ((coords[i] - minVal) * 
                             (256.0f/(maxVal-minVal))) - 128.0f);
        if (icoords[i] == 128)
          icoords[i] = 127;
      }

      if (coordType.equals("verts"))
        printVertsInfo(icoords);
      printCoords(icoords, coordType, numVals);
    }
    else if (coordType.equals("colors")) {
       // colour are scaled to integers in the range 0 to 255
      System.out.println("Rescale " + coordType + " coords to [0 to 255]");
      for(int i=0; i < coords.length; i ++) {
        icoords[i] = (int) Math.round( (coords[i] - minVal) * 
                             (256.0f/(maxVal-minVal)) );
        if (icoords[i] == 256)
          icoords[i] = 255;
      }
      printCoords(icoords, coordType, numVals);
    }
    else if (coordType.equals("texCoords")) {
      System.out.println("No rescaling of " + coordType + " coords ");
      printTexCoords(coords, numVals);
    }
  }  // end of printShapeCoords()


  private void printVertsInfo(int[] icoords)
  /* Write out the shapes (x,y,z) center and the scaling needed to
     make its largest dimension equal to 1. They are calculated on the
     integer coordinates for the shape, stored in icoords[].

     If there are multiple shapes in the model, then a number is included
     at the end of the variable names. */
  {
    // use ModelDimension to calculate the center and scale factor
    ModelDimensions modelDims = new ModelDimensions();
    modelDims.set(icoords[0], icoords[1], icoords[2]);
    for(int i= 3; i < icoords.length; i+=3)
      modelDims.update(icoords[i], icoords[i+1], icoords[i+2]);

    // modelDims.reportDimensions();  
        // dimensions of model (before centering and scaling)

    fileWrite("  // position and scaling info\n");

    // get the model's center point
    Tuple3 center = modelDims.getCenter();
    System.out.println("Center pt: " + center);
    fileWrite("  private static final float xCenter = " + center.getX() + "f;\n");
    fileWrite("  private static final float yCenter = " + center.getY() + "f;\n");
    fileWrite("  private static final float zCenter = " + center.getZ() + "f;\n");

    // calculate the scale factor
    float scaleFactor = 1.0f;
    float largest = modelDims.getLargest();
    System.out.println("Largest dimension: " + largest);
    if (largest != 0.0f)
      scaleFactor = (1.0f / largest);    // so largest dim is scaled to 1.0

    System.out.println("Scale factor: " + scaleFactor);
    fileWrite("  private static final float scaleFactor = " + 
                                                  scaleFactor + "f;\n\n");
  }  // end of printVertsInfo()


  private void printCoords(int[] icoords, String coordType, int numVals)
  /* Print the integer values in icoords[] as arrays into examObj.txt
       * vertices --> byte[] verts  (range -128 to 127)
       * normals --> byte[] normals (range -128 to 127)
       * colour coords --> byte[] colors  (range 0 to 255)
     If there are multiple shapes in the model, then a number is included
     at the end of the array names.
  */
  { int coordsLen = icoords.length;

    if (coordsLen > MAX_ELEMS)
      System.out.println("WARNING: " + coordType + " array very long: " +
                                                       coordsLen + " values");
      /* The method will probably not compile. Even if it's split into parts
         the emulator cannot handle arrays bigger than 32 Kb. */


    fileWrite("  // " + coordType + " coords [" +
                     coordsLen + " values/" + numVals +  " = " + 
                           (coordsLen/numVals) + " points] \n");
    if (coordsLen > MAX_ELEMS)
      fileWrite("  // WARNING: array very long\n");
    fileWrite("  private static final byte[] " + coordType + " = {\n   ");

    int coordCount = 0;
    for(int i=0; i < coordsLen; i += numVals) {
      printCoord(icoords, i, numVals, coordsLen); 
      coordCount++;
      if (coordCount == 4) {   // 4 coords per line
        coordCount = 0;
        fileWrite("\n");
      }
    }
    fileWrite("\n  };  // end of " + coordType + "[]\n\n\n" );
  }  // end of printCoords()



  private void printCoord(int[] iVerts, int posn, int numVals, int coordsLen)
  // print the multiple values making up a single coordinate
  {
    fileWrite("\t");
    for (int i=0; i < numVals; i++) {
      if ((posn+i) == (coordsLen-1))   // if the last point
        fileWrite("" + iVerts[posn+i]);  // then don't print a comma
      else
        fileWrite(iVerts[posn+i] + ",");
    }
    fileWrite("  ");
  } // end of printCoord()



  private void printTexCoords(float[] coords, int numVals)
  /* Print the tex coords in coords[] as a float array into examObj.txt
     If there are multiple shapes in the model, then a number is included
     at the end of the array names.
  */
  { int coordsLen = coords.length;

    if (coordsLen > MAX_ELEMS)
      System.out.println("WARNING: tex coords array very long: " +
                                                       coordsLen + " values");
      /* The method will probably not compile. Even if it's split into parts
         the emulator cannot handle arrays bigger than 32 Kb. */


    fileWrite("  // tex coords [" +
                     coordsLen + " values/" + numVals +  " = " + 
                           (coordsLen/numVals) + " points] \n");
    if (coordsLen > MAX_ELEMS)
      fileWrite("  // WARNING: array very long\n");
    fileWrite("  private static final float[] texCoords = {\n   ");

    int coordCount = 0;
    for(int i=0; i < coordsLen; i += numVals) {
      printTexCoord(coords, i, numVals, coordsLen); 
      coordCount++;
      if (coordCount == 3) {   // 3 coords per line, since they're floats
        coordCount = 0;
        fileWrite("\n");
      }
    }
    fileWrite("\n  };  // end of texCoords[]\n\n\n" );
  }  // end of printTexCoords()


  private void printTexCoord(float[] verts, int posn, int numVals, int coordsLen)
  // print the multiple values making up a single coordinate
  {
    fileWrite("\t");
    for (int i=0; i < numVals; i++) {
      if ((posn+i) == (coordsLen-1))   // if the last point
        fileWrite("" + verts[posn+i] + "f");  // then don't print a comma
      else
        fileWrite(verts[posn+i] + "f, ");
    }
    fileWrite("  ");
  } // end of printTexCoord()




  private void stripInfo(TriangleStripArray tsa)
  /* Output an array of strip lengths which states how the data in the
     interleaved array is split into distinct triangle strips.

     The data is embedded is written to examObj.txt

     If there are multiple shapes in the model, then a number is included
     at the end of the array name.
  */
  {
    int numStrips = tsa.getNumStrips();
    System.out.println("\nNum. strips: " + numStrips);

    int[] svCounts = new int[numStrips];
    tsa.getStripVertexCounts(svCounts);

    fileWrite("  // an array holding triangle strip lengths\n" );
    fileWrite("  private static final int[] strips = {\n      " );

    int stripsCount = 0;
    int numPoints = 0;
    for(int i=0; i < numStrips-1; i++) {
      fileWrite(svCounts[i] + ", ");
      numPoints += svCounts[i];
      stripsCount++;
      if (stripsCount == 20) {   // 20 numbers per line
        stripsCount = 0;
        fileWrite("\n      ");
      }
    }
    fileWrite(svCounts[numStrips-1] + "\n");
    numPoints += svCounts[numStrips-1];
    fileWrite("  };  // (" + numPoints + " points)\n\n\n" );
  } // end of stripInfo()



  // -------------------- report on appearance (colour, shininess) ----------

  private void printAppearance(Appearance app)
  /* Report on the colour attributes, material, texture, and
     texture attributes (if they exist). The material and texture
     details are written to examObj.txt

     Switch on texture modulation if a Material
     and Texture are detected together
  */
  {
     ColoringAttributes ca = app.getColoringAttributes();
     if (ca != null)
       System.out.println(ca.toString());
     else
       System.out.println("No colouring attributes");

     Material mat = app.getMaterial();
     if (mat != null) {
       System.out.println(mat.toString());
       printMaterial(mat);
     }
     else
       System.out.println("No material");

     Texture tex = app.getTexture();
     if (tex != null) {
       ImageComponent[] ims = tex.getImages();
       System.out.println("Num. texture images: " + ims.length);
       for (int i=0; i < ims.length; i++)
         System.out.print("(" + ims[i].getWidth() + "," + ims[i].getHeight() + ") ");
       System.out.println("");
     }
     else
       System.out.println("No Texture");

     if (tex != null) {
       int bmS = tex.getBoundaryModeS();
       int bmT = tex.getBoundaryModeT();
       if ((bmS == Texture.WRAP) && (bmT == Texture.WRAP))
         System.out.println("Texture Boundary Modes both wrap");
       else if ((bmS == Texture.CLAMP) && (bmT == Texture.CLAMP))
         System.out.println("Texture Boundary Modes are both clamped");
       else
         System.out.println("Texture Boundary Modes (s,t): (" +
                              bmS + ", " + bmT + ")");
     }

     TextureAttributes ta;
     if ((mat != null) && (tex != null)) {
       System.out.println("Setting Material and Texture modulation");
       ta = app.getTextureAttributes();
       if (ta == null)   // no texture attributes found
         ta = new TextureAttributes();
       ta.setTextureMode(TextureAttributes.MODULATE);
       app.setTextureAttributes(ta);
     }

     ta = app.getTextureAttributes();
     if (ta == null)
       System.out.println("No Texture Attributes");
     // else
     //   System.out.println(ta.toString());

  }  // end of printAppearance()



  private void printMaterial(Material mat)
  /*  Write out the material's ambient, emissive, diffuse, and specular color 
      details into various arrays, and the texture name and
      shininess setting.

     If there are multiple shapes in the model, then a number is included
     at the end of the variable names.
  */
  {
    fileWrite("  // materials\n" );

    if (numTextures != 0)    // there is a texture for this model
      fileWrite("  private static final String TEX_FNM = \"/" + 
                                       modelNm + ".png\";  // check this\n\n");
    else  // no texture
      fileWrite("  private static final String TEX_FNM = \"\";  // not used\n\n");


    Color3f col = new Color3f();
    mat.getAmbientColor(col);
    fileWrite("  private static final float[] ambientMat = {" +
                          writeColours(col) + "};\n");

    mat.getEmissiveColor(col);
    fileWrite("  private static final float[] emissiveMat = {" +
                           writeColours(col) + "};\n"); 

    mat.getDiffuseColor(col);
    fileWrite("  private static final float[] diffuseMat = {" +
                           writeColours(col) + "};\n"); 

    mat.getSpecularColor(col);
    fileWrite("  private static final float[] specularMat = {" +
                           writeColours(col) + "};\n"); 

    fileWrite("  private static final float shininess = " + 
                           mat.getShininess() + "f;\n\n");
  }  // end of printMaterial()


  private String writeColours(Color3f col)
  /* Return the RGB color information (0.0f to 1.0f for each component),
     with alpha always 1.0f. */
  {
    return "" + col.x + "f, " + col.y + "f, " + col.z + "f, 1.0f";
    // fileWrite("RGB Colour: (" + col.x + "," + col.y + "," + col.z + ")\n");
  }  // end of writeColours()



  // ------------------- file writing utility -----------------

  private void fileWrite(String s)
  { try {
      ofw.write(s);
    }
    catch( IOException ioe )
    { System.err.println("Cannot write '" + s + "' to " + EXAMINE_FN); }
  }  // end of fileWrite()


} // end of WrapObjView class
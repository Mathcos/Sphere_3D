
JOGL-ES Chapter 2. Loading OBJ Models

From:
  Pro Java 6 3D Game Development 

  Dr. Andrew Davison
  Dept. of Computer Engineering
  Prince of Songkla University
  Hat Yai, Songkhla 90112, Thailand
  E-mail: ad@fivedots.coe.psu.ac.th

  Web Site for the book: http://fivedots.coe.psu.ac.th/~ad/jg2/


If you use this code, please mention my name, and include a link
to the book's Web site.

**NOTE**: this example is NOT in the hardcopy book printed by Apress.

Thanks,
  Andrew


============================
ObjView

ObjView is a Java 3D application. To compile and run it, Java 3D
must be installed. Get a copy from https://java3d.dev.java.net/

------
Files in the Download

ObjView consists of 4 Java files:
  ObjView.java, WrapObjView.java,
  ModelDimensions.java, Tuple3.java,               

1 text file:
  examObj.txt

A models/ subdirectory where the OBJ files must be located
in order for ObjView to see them.


------
Compilation and Execution

Compilation:
> javac *.java

Execution:
> java ObjView <OBJ filename>	
      // the OBJ file must be in models/ subdirectory

e.g.
> java ObjView hand.obj

or
> java ObjView penguin.obj

A (lengthy) examObj.txt file will be generated, containing the 
model arrays and variables that need to be pasted into a 
copy of OBJShape.java.

OBJShape.java can be found in the ViewerES application directory.

------
More on models/

models/ contains 13 files:

Six OBJ files:
  barbell.obj, cube.obj, hand.obj,
  humanoid.obj, penguin.obj, sword.obj

Five MTL files:
  barbell.mtl, cube.mtl, humanoid.mtl, 
  penguin.mtl, sword.mtl
  (there isn't a MTL file for the hand)

Two textures in PNG files:
  penguin.png, sword.png
     
------
Conversion Notes

The penguin (penguin.obj, penguin.mtl, and penguin.png)
and the hand (hand.obj) have already been converted to
versions of OBJShape. Their classes are called PenguinModel
and HandModel in ViewerES.

barbell.obj generates multiple sets of arrays and variables, 
which will need to be pasted into multiple copies of OBJShape.java.
See section 5 of the chapter for details.

humanoid.obj generates arrays which are too large for WTK. 
See section 6 of the chapter for details.

---------
Last updated: 27th April 2007

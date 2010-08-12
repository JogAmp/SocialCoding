package framework.base;

/**                                                                                               
 **   __ __|_  ___________________________________________________________________________  ___|__ __
 **  //    /\                                           _                                  /\    \\  
 ** //____/  \__     __ _____ _____ _____ _____ _____  | |     __ _____ _____ __        __/  \____\\ 
 **  \    \  / /  __|  |     |   __|  _  |     |  _  | | |  __|  |     |   __|  |      /\ \  /    /  
 **   \____\/_/  |  |  |  |  |  |  |     | | | |   __| | | |  |  |  |  |  |  |  |__   "  \_\/____/   
 **  /\    \     |_____|_____|_____|__|__|_|_|_|__|    | | |_____|_____|_____|_____|  _  /    /\     
 ** /  \____\                       http://jogamp.org  |_|                              /____/  \    
 ** \  /   "' _________________________________________________________________________ `"   \  /    
 **  \/____.                                                                             .____\/     
 **             
 ** More or less some kind of wrapper class wich encalsulates a convenient runtime environment
 ** for classes implementing the BaseRoutineInterface. Also adds some global debug information
 ** overlay. 
 **
 **/

import java.awt.*;
import java.awt.image.BufferedImage;
import java.lang.reflect.Constructor;
import java.text.*;
import javax.media.opengl.*;
import javax.media.opengl.glu.*;
import com.jogamp.opengl.util.gl2.*;
import com.jogamp.opengl.util.awt.*;
import static javax.media.opengl.GL.*;
import static javax.media.opengl.GL3bc.*;

public class BaseRoutineRuntime {

	private TextRenderer mTextRenderer;
	private DecimalFormat mDecimalFormat;
	private BaseGlobalEnvironment mBaseGlobalEnvironment;
	private int mFrameCounter;
	private long mCurrentFrameRenderingTimeStart;
	private long mCurrentFrameRenderingTimeEnd;
	private long mLastFrameRenderingTimeStart;
	private long mLastFrameRenderingTimeEnd;
	private BaseRoutineInterface mBaseRoutineInterface;
	private long mAverageFramerateCounter;
	private long mAverageFramerateTimeStart;
	private long mAverageFramerateTimeEnd;
	private static final int cAverageFramerateInterval = 25;
	private int mAverageFramerate;
	
	public BaseRoutineRuntime() {
		//Zzzz ... :>
	}
	
	public void initRuntime(GL2 inGL,GLU inGLU,GLUT inGLUT) {
    	mBaseGlobalEnvironment = BaseGlobalEnvironment.getInstance();
		Font tFont = new Font("SansSerif", Font.PLAIN, 12);
    	mTextRenderer = new TextRenderer(tFont, false, false);
    	mDecimalFormat = new DecimalFormat("###,###,###,###,###.###");
		mFrameCounter = 0;
		try {
			BaseLogging.getInstance().info("CREATING BASEROUTINE CONSTRUCTOR FOR "+mBaseGlobalEnvironment.getBaseRoutineClassName());
			Class<?> tIntermediateClass = Class.forName(mBaseGlobalEnvironment.getBaseRoutineClassName());
			Class<? extends BaseRoutineAdapter> tIntermediateSubclass = tIntermediateClass.asSubclass(BaseRoutineAdapter.class);
			Constructor<? extends BaseRoutineAdapter> tIntermediateSubclassConstructor = tIntermediateSubclass.getConstructor();
			mBaseRoutineInterface = tIntermediateSubclassConstructor.newInstance();
			BaseLogging.getInstance().info("BASEROUTING INSTANCE NAMED "+mBaseGlobalEnvironment.getBaseRoutineClassName()+" CREATED ...");
			BaseLogging.getInstance().info("INITIALIZING BASEROUTINE ...");
			mBaseRoutineInterface.initRoutine(inGL,inGLU,inGLUT);
		} catch (Exception e) {
			BaseLogging.getInstance().fatalerror(e);
		}
	}
	
	public void mainLoopRuntime(GL2 inGL,GLU inGLU,GLUT inGLUT) {
		mLastFrameRenderingTimeStart = mCurrentFrameRenderingTimeStart;
		mLastFrameRenderingTimeEnd = System.nanoTime();
		mCurrentFrameRenderingTimeStart = System.nanoTime();
		//---
		//create default frustum state ...
		resetFrustumToDefaultState(inGL,inGLU,inGLUT);
		//clear screen and z-buffer ...
		inGL.glClearColor(0.0f, 0.0f, 0.0f, 0.0f);
	    inGL.glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
	    mBaseRoutineInterface.mainLoop(mFrameCounter,inGL,inGLU,inGLUT);
	    checkForGlError(inGL,inGLU);
	    //optional fraps/kkapture-style screencapture logging ... ->=:-)X	   
	    if (mBaseGlobalEnvironment.wantsFrameCapture()) {
	    	BufferedImage tScreenshot = Screenshot.readToBufferedImage(0,0, mBaseGlobalEnvironment.getScreenWidth(), mBaseGlobalEnvironment.getScreenHeight(),false);
        	BaseLogging.getInstance().logCapture(tScreenshot, mFrameCounter);
    	}
	    //---
	    mCurrentFrameRenderingTimeEnd = System.nanoTime();
	    renderDebugInformation(inGL,inGLU,inGLUT);
	    mFrameCounter++;
	}
	
	public void cleanupRuntime(GL2 inGL,GLU inGLU,GLUT inGLUT) {
		mBaseRoutineInterface.cleanupRoutine(inGL,inGLU,inGLUT);
	}
	
	/* --------------------------------------------------------------------------------------------------------------------------------------------------- */
	
	public static void resetFrustumToDefaultState(GL2 inGL,GLU inGLU,GLUT inGLUT) {
		inGL.glViewport(0, 0, BaseGlobalEnvironment.getInstance().getScreenWidth(), BaseGlobalEnvironment.getInstance().getScreenHeight());
		inGL.glMatrixMode(GL_PROJECTION);
		inGL.glLoadIdentity();
        double tAspectRatio = (double)BaseGlobalEnvironment.getInstance().getScreenWidth() / (double)BaseGlobalEnvironment.getInstance().getScreenHeight();
        inGLU.gluPerspective(45.0, tAspectRatio, 0.01, 200.0);
        inGL.glMatrixMode(GL_MODELVIEW);
        inGL.glLoadIdentity();
        inGLU.gluLookAt(
        		0f, 0f, 70f,
                0f, 0f, 0f,
                0.0f, 1.0f, 0.0f
        );
	}
	
	private void renderDebugInformation(GL2 inGL,GLU inGLU,GLUT inGLUT) {
        if (++mAverageFramerateCounter == cAverageFramerateInterval) {
            mAverageFramerateTimeEnd = System.nanoTime();
            mAverageFramerateCounter = 0;
            mAverageFramerate = (int)(1000000000.0f/((mAverageFramerateTimeEnd - mAverageFramerateTimeStart)/cAverageFramerateInterval));   
            mAverageFramerateTimeStart = System.nanoTime();     
        }
		long tPossibleFrameRate = (long)(1000000000.0f/(mCurrentFrameRenderingTimeEnd-mCurrentFrameRenderingTimeStart));
		long tActualFrameRate = (long)(1000000000.0f/(mLastFrameRenderingTimeEnd-mLastFrameRenderingTimeStart));
		String[] tDebugInformation = new String[5];
        tDebugInformation[0] = "JOGL: "+"GL_VENDOR:"+inGL.glGetString(GL_VENDOR)+" GL_RENDERER:"+inGL.glGetString(GL_RENDERER);
        tDebugInformation[1] = "GL_VERSION: "+inGL.glGetString(GL_VERSION)+" GLSL_VERSION: "+inGL.glGetString(GL_SHADING_LANGUAGE_VERSION); 
        tDebugInformation[2] = "VMMEM: USED: "+mDecimalFormat.format(mBaseGlobalEnvironment.getUsedMem())+" FREE: "+mDecimalFormat.format(mBaseGlobalEnvironment.getFreeMem())+" TOTAL: "+mDecimalFormat.format(mBaseGlobalEnvironment.getTotalMem())+" MAX: "+mDecimalFormat.format(mBaseGlobalEnvironment.getMaxMem());
        tDebugInformation[3] = "DISPLAY RESOLUTION: "+mBaseGlobalEnvironment.getScreenWidth()+"x"+mBaseGlobalEnvironment.getScreenHeight()+" FRAME: "+mFrameCounter+" AVERAGE FPS:"+mAverageFramerate+" ACTUAL FPS: "+tActualFrameRate+" POSSIBLE FPS: "+mDecimalFormat.format(tPossibleFrameRate);    
        tDebugInformation[4] = "ROUTINE: "+mBaseGlobalEnvironment.getBaseRoutineClassName();
        for (int i=0; i<tDebugInformation.length; i++) {
	    	mTextRenderer.beginRendering(BaseGlobalEnvironment.getInstance().getScreenWidth(), BaseGlobalEnvironment.getInstance().getScreenHeight());
		    mTextRenderer.setColor(1.0f, 1.0f, 1.0f, 1.0f);
		    mTextRenderer.draw(tDebugInformation[i], 0, BaseGlobalEnvironment.getInstance().getScreenHeight()-11*(i+1));
		    mTextRenderer.endRendering();
        }
	}
	
    private void checkForGlError(GL2 inGL,GLU inGLU) {
    	int tError = inGL.glGetError();
	    String tErrorString = "!!! GL-ERROR !!! GLU ERROR STRING FOR ERROR="+inGLU.gluErrorString(tError);
	    if (tError!=GL_NO_ERROR) {
	    	BaseLogging.getInstance().error(tErrorString);
	    }
    }
	
}
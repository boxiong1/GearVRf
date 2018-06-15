/* Copyright 2015 Samsung Electronics Co., LTD
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gearvrf.debug;

import java.util.ArrayList;
import java.util.List;

import org.gearvrf.GVRBitmapImage;
import org.gearvrf.GVRCamera;
import org.gearvrf.GVRCameraRig;
import org.gearvrf.GVRContext;
import org.gearvrf.GVRImage;
import org.gearvrf.GVRMaterial;
import org.gearvrf.GVRScene;
import org.gearvrf.GVRShader;
import org.gearvrf.GVRShaderData;
import org.gearvrf.GVRShaderId;
import org.gearvrf.GVRShaderTemplate;
import org.gearvrf.GVRTexture;
import org.gearvrf.R;

import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;

import org.gearvrf.utility.TextFile;

/**
 * Show the performance information on screen
 */

public class GVRPerformance extends GVRMaterial
{

    public static class PerformanceShader extends GVRShaderTemplate
    {
        static private String vertexShader;
        static private String fragmentShader;

        public PerformanceShader(GVRContext ctx)
        {
            super("", "sampler2D u_texture sampler2D u_overlay", "float3 a_position float2 a_texcoord",GLSLESVersion.V300);
            if (vertexShader == null)
            {
                vertexShader = TextFile.readTextFile(ctx.getContext(), R.raw.posteffect_quad);
            }
            if (fragmentShader == null)
            {
                fragmentShader = TextFile.readTextFile(ctx.getContext(), R.raw.hud_console);
            }
            setSegment("FragmentTemplate", fragmentShader);
            setSegment("VertexTemplate", vertexShader);
        }
    }

    /**
     * Specify where the message(s) are displayed. You pass an {@code EyeMode}
     * value to {@linkplain GVRPerformance#GVRPerformance(GVRContext, EyeMode) the
     * constructor} but can change it <i>via</i>
     * {@link GVRPerformance#setEyeMode(EyeMode)}
     */
    public enum EyeMode {
        /** This console's messages will be displayed on the left eye */
        LEFT_EYE,
        /** This console's messages will be displayed on the right eye */
        RIGHT_EYE,
        /** This console's messages will be displayed on both eyes */
        BOTH_EYES,
        /**
         * This console's messages will not be displayed. Specifying
         * {@link #NEITHER_EYE} means that you do have a component updating a
         * console texture every time you call
         * {@link GVRPerformance#writeLine(String, Object...) writeLine()}, but that
         * component is <em>not</em> in the render pipeline, so does not add to
         * render costs.
         */
        NEITHER_EYE
    };

    private EyeMode eyeMode;
    private int textColor;
    private float textSize;

    private final List<Float> fps = new ArrayList<Float>();

    private static final int MAX_NUM_OF_FPS = 21; // num of fps values
    private int availMem;
    private float cpu_temp;
    private int cpu_level;
    private int gpu_level;
    private boolean mIsUpdate = false;

    private Bitmap HUD = Bitmap.createBitmap(HUD_WIDTH, HUD_HEIGHT,
            Config.ARGB_8888);
    private Canvas canvas = new Canvas(HUD);
    private final Paint paint = new Paint();
    private final float defaultTextSize = paint.getTextSize();
    private GVRTexture texture = null;
    private int hudWidth = HUD_WIDTH;
    private int hudHeight = HUD_HEIGHT;

    /**
     * Create a console, specifying the initial eye mode.
     *
     * @param gvrContext
     *            The GVR context.
     * @param startMode
     *            The initial eye mode; you can change this <i>via</i>
     *            {@link #setEyeMode(EyeMode)}
     */
    public GVRPerformance(GVRContext gvrContext, EyeMode startMode) {
        this(gvrContext, startMode, gvrContext.getMainScene());
    }

    /**
     * Create a console, specifying the initial eye mode and the
     * {@link GVRScene} to attach it to.
     *
     * This overload is useful when you are using
     * {@link GVRContext#getMainScene()} and creating your debug console in
     * {@link org.gearvrf.GVRMain#onInit(GVRContext)}.
     *
     * @param gvrContext
     *            The GVR context.
     * @param startMode
     *            The initial eye mode; you can change this <i>via</i>
     *            {@link #setEyeMode(EyeMode)}
     * @param gvrScene
     *            The {@link GVRScene} to attach the console to; this is useful
     *            when you want to attach the console to the
     *            {@linkplain GVRContext#getMainScene() next main scene.}
     */
    public GVRPerformance(GVRContext gvrContext, EyeMode startMode,
                      GVRScene gvrScene) {
        super(gvrContext, getShaderId(gvrContext));
        setEyeMode(startMode, gvrScene.getMainCameraRig());
        setMainTexture();

        setTextColor(DEFAULT_COLOR);
        setTextSize(1);
        paint.setAntiAlias(true);
    }

    /**
     * Get the text color.
     *
     * @return The current text color, in Android {@link Color} format
     */
    public int getTextColor() {
        return textColor;
    }

    /**
     * Set the text color.
     *
     * @param color
     *            The text color, in Android {@link Color} format. The
     *            {@linkplain Color#alpha(int) alpha component} is ignored.
     */
    public void setTextColor(int color) {
        textColor = color;
        paint.setColor(textColor);
    }

    /**
     * Get the current text size.
     *
     * The default text size is somewhat bigger than the default Android
     * {@link Paint} text size: this method returns the current text as a
     * multiple of this component's default text size, not the standard Android
     * text size.
     *
     * @return The current text size factor.
     */
    public float getTextSize() {
        return textSize;
    }

    /**
     * Set the text size.
     *
     * @param newSize
     *            The new text size, as a multiple of the default text size.
     */
    public void setTextSize(float newSize) {
        textSize = newSize;
        paint.setTextSize(defaultTextSize * textSize);
    }

    /**
     * Get the current eye mode, or where the console messages are displayed.
     *
     * This may be the value passed to
     * {@linkplain #GVRPerformance(GVRContext, EyeMode) the constructor,} but you
     * can also change that at any time with {@link #setEyeMode(EyeMode)}.
     *
     * @return The current eye mode.
     */
    public EyeMode getEyeMode() {
        return eyeMode;
    }

    /**
     * Set the current eye mode, or where the console messages are displayed.
     *
     * Always 'edits' the list of post-effects; setting the mode to
     * {@link EyeMode#NEITHER_EYE} means this component will not affect render
     * times at all.
     *
     * @param newMode
     *            Left, right, both, or neither.
     */
    public void setEyeMode(EyeMode newMode) {
        setEyeMode(newMode, getGVRContext().getMainScene().getMainCameraRig());
    }

    private void setEyeMode(EyeMode newMode, GVRCameraRig cameraRig) {
        eyeMode = newMode;

        GVRCamera leftCamera = cameraRig.getLeftCamera();
        GVRCamera rightCamera = cameraRig.getRightCamera();

        // Remove from both (even if not present) add back later
        leftCamera.removePostEffect(this);
        rightCamera.removePostEffect(this);

        if (eyeMode == EyeMode.LEFT_EYE || eyeMode == EyeMode.BOTH_EYES) {
            leftCamera.addPostEffect(this);
        }
        if (eyeMode == EyeMode.RIGHT_EYE || eyeMode == EyeMode.BOTH_EYES) {
            rightCamera.addPostEffect(this);
        }
    }

    /**
     * Clear the console of text
     *
     * Clear the console of any written text.
     */
    public void clear() {
        fps.clear();
    }


    /**
     * Sets the width and height of the canvas the text is drawn to.
     *
     * @param width
     *     width of the new canvas.
     *
     * @param height
     *     hegiht of the new canvas.
     *
     */
    public void setCanvasWidthHeight(int width, int height) {
        hudWidth = width;
        hudHeight = height;
        HUD = Bitmap.createBitmap(width, height, Config.ARGB_8888);
        canvas = new Canvas(HUD);
        texture = null;
    }

    /**
     * Get the width of the text canvas.
     *
     * @return the width of the text canvas.
     */
    public int getCanvasWidth() {
        return hudWidth;
    }

    /**
     * Get the height of the text canvas.
     *
     * @return the height of the text canvas.
     */
    public int getCanvasHeight() {
        return hudHeight;
    }


    private static void log(String TAG, String pattern, Object... parameters) {
        // Log.d(TAG, pattern, parameters);
    }

    public void updateParams(float fps_value, int mem, float temp, int cpu, int gpu) {
        fps.add(fps_value);
        if(fps.size() > MAX_NUM_OF_FPS)
        {
            fps.remove(0);
        }

        availMem = mem;
        cpu_temp = temp;
        cpu_level = cpu;
        gpu_level = gpu;

        mIsUpdate = true;
    }

    public boolean isUpdated() {
        return mIsUpdate;
    }

    public void updateHUD() {
        // TODO Line wrap!

        HUD.eraseColor(Color.TRANSPARENT);

        float xOffset = hudWidth / 2.0f - 210.0f;
        float yOffset = hudWidth / 2.0f + 210.0f;
        paint.setColor(Color.BLUE);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2);

        canvas.drawLine(xOffset, yOffset - 200.0f, xOffset, yOffset, paint);
        canvas.drawLine(xOffset, yOffset, xOffset + 200.f, yOffset, paint);

        int showNum = Math.min(MAX_NUM_OF_FPS, fps.size());

        Path path = new Path();
        int index = fps.size() - showNum;
        path.moveTo(xOffset + index * 10.0f, yOffset - fps.get(index)*3.0f);

        for(index = fps.size() - showNum + 1; index < fps.size(); index++)
        {
            path.lineTo(xOffset + index * 10.0f, yOffset - fps.get(index) * 3.0f);
        }
        paint.setColor(Color.RED);
        paint.setStrokeWidth(1);

        canvas.drawPath(path, paint);

        paint.setColor(Color.BLUE);

        float textHeight = paint.getFontSpacing();
        yOffset += textHeight/3.0f;
        canvas.drawText("Frames Per Second", xOffset + 20.0f, yOffset - 200.0f, paint);
        canvas.drawText("0", xOffset - paint.measureText("0 "), yOffset, paint);
        canvas.drawText("60", xOffset - paint.measureText("60 "), yOffset - 180.0f, paint);

        xOffset = hudWidth / 2.0f + 50.0f;
        yOffset = hudHeight / 2.0f + 20.0f;

        String lineText = String.format("FPS : %.2f", fps.get(showNum - 1));
        canvas.drawText(lineText, xOffset, yOffset, paint);

        lineText = String.format("Mem : %d", availMem);
        yOffset += textHeight;
        canvas.drawText(lineText, xOffset, yOffset, paint);

        lineText = String.format("Temp : %.1f", cpu_temp);
        yOffset += textHeight;
        canvas.drawText(lineText, xOffset, yOffset, paint);

        lineText = String.format("CPU : %d", cpu_level);
        yOffset += textHeight;
        canvas.drawText(lineText, xOffset, yOffset, paint);

        lineText = String.format("GPU : %d", gpu_level);
        yOffset += textHeight;
        canvas.drawText(lineText, xOffset, yOffset, paint);

        setMainTexture();

        mIsUpdate = false;

    }

    private void setMainTexture() {

        Boolean textureUpdated = false;
        if (texture == null)
        {
            texture = new GVRTexture(getGVRContext());
        }
        GVRImage image = texture.getImage();
        if (image != null)
        {
            if (GVRBitmapImage.class.isAssignableFrom(image.getClass()))
            {
                GVRBitmapImage bmapImage = (GVRBitmapImage) image;
                bmapImage.setBitmap(HUD);
                textureUpdated = true;
            }
        }
        if (!textureUpdated)
        {
            image = new GVRBitmapImage(getGVRContext(), HUD);
            texture.setImage(image);
            setTexture("u_overlay", texture);
        }
    }

    private static synchronized GVRShaderId getShaderId(GVRContext gvrContext) {
        if (shaderId == null)
        {
            shaderId = gvrContext.getShaderManager().getShaderType(PerformanceShader.class);
        }
        return shaderId;
    }

    private static GVRShaderId shaderId;

    static {
        GVRContext.addResetOnRestartHandler(new Runnable() {

            @Override
            public void run() {
                shaderId = null; // should be enough
            }
        });
    }

    private static final int HUD_HEIGHT = 1024;
    private static final int HUD_WIDTH = 1024;
    private static final int DEFAULT_COLOR = Color.BLUE;
    /**
     * The baseline calculation seems right ... looks like some of the scene is
     * getting stenciled out
     */
    private static final float TOP_FUDGE = 20;
}



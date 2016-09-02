package com.wizzer.mle.parts.actors;

import com.wizzer.mle.runtime.core.IMleProp;
import com.wizzer.mle.runtime.core.MleActor;
import com.wizzer.mle.runtime.core.MleMediaRef;
import com.wizzer.mle.runtime.dpp.MleDppException;
import com.wizzer.mle.runtime.dpp.MleDppLoader;
import com.wizzer.mle.runtime.scheduler.MleScheduler;
import com.wizzer.mle.runtime.scheduler.MlePhase;
import com.wizzer.mle.runtime.scheduler.MleTask;
import com.wizzer.mle.runtime.MleTitle;
import com.wizzer.mle.runtime.core.MleRuntimeException;

import com.wizzer.mle.parts.mrefs.MleTextureMapMediaRef;
import com.wizzer.mle.parts.mrefs.MleModelMediaRef;

import com.wizzer.mle.parts.props.Mle3dModelProperty;
import com.wizzer.mle.parts.props.Mle3dTranslationProperty;
import com.wizzer.mle.parts.props.Mle3dQuaternionRotationProperty;
import com.wizzer.mle.parts.props.Mle3dNonuniformScaleProperty;
import com.wizzer.mle.parts.props.Mle3dTextureMapProperty;

import com.wizzer.mle.math.MlMath;
import com.wizzer.mle.math.MlVector3;
import com.wizzer.mle.math.MlRotation;
import com.wizzer.mle.math.MlScalar;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;

/**
 * Created by msm on 8/24/16.
 */
public class MleModelActor extends MleActor
{
    // The properties exposed in the DWP are "position", "orientation", "scale", "model"
    // and "texture".
    public Mle3dTranslationProperty        position;
    public Mle3dQuaternionRotationProperty orientation;
    public Mle3dNonuniformScaleProperty    scale;
    public Mle3dModelProperty              model;
    public Mle3dTextureMapProperty         texture;

    // This class is used to perform the behavior (via the Scheduler's Task).
    private class DoBehave implements Runnable
    {
        // The actor which will perform the behavior.
        private MleModelActor m_actor = null;

        // Use constructor to set actor.
        public DoBehave(MleModelActor actor) { m_actor = actor; }

        // Execute the behavior.
        public void run()
        {
            if (m_actor != null)
                MleModelActor.behave(m_actor);
        }

        // Hide default constructor.
        private DoBehave() {}
    }

    // The behavior task executed during the Actor phase.
    private MleTask m_behaveTask = null;

    /**
     * The default constructor.
     */
    public MleModelActor() { super(); }

    /* (non-Javadoc)
     * @see com.wizzer.mle.runtime.core.MleActor#init()
     */
    public void init() throws MleRuntimeException
    {
        // Update the Role by pushing the property values.
        if (texture != null) texture.push(this);
        if (model != null) model.push(this);
        update();

        // Register with the scheduler.
        MleScheduler scheduler = MleTitle.getInstance().m_theScheduler;
        MlePhase actorPhase = MleTitle.g_theActorPhase;
        if (actorPhase == null)
            throw new MleRuntimeException("MleModelActor: Actor phase does not exist.");
        m_behaveTask = new MleTask(new DoBehave(this));
        scheduler.addTask(actorPhase, m_behaveTask);
    }

    /* (non-Javadoc)
     * @see com.wizzer.mle.runtime.core.MleActor#dispose()
     */
    public void dispose() throws MleRuntimeException
    {
        // Remove the behave function from the scheduler.
        MleScheduler scheduler = MleTitle.getInstance().m_theScheduler;
        MlePhase actorPhase = MleTitle.g_theActorPhase;
        if (actorPhase == null)
            throw new MleRuntimeException("MleModelActor: Actor phase does not exist.");
        actorPhase.deleteTask(m_behaveTask);
        m_behaveTask = null;
    }

    public void update()
    {
        try {
            // Update transform-related properties only.
            if (scale != null) scale.push(this);
            if (orientation != null) orientation.push(this);
            if (position != null) position.push(this);
        } catch (MleRuntimeException ex)
        {
            // ToDo: do we just ignore the fault?
        }
    }

    // Change in rotation; currently a constant spin around y axis.
    static private MlRotation m_delta = null;

    static void behave(MleModelActor actor)
    {
        // Define spin parameters.
        if (m_delta == null)
            m_delta = new MlRotation(new MlVector3(
                MlScalar.ML_SCALAR_ZERO, MlScalar.ML_SCALAR_ONE, MlScalar.ML_SCALAR_ZERO),
                (float)0.035);

        // Update rotational behavior.
        //actor.orientation.m_rotation *= m_delta;
        MlRotation rotation = actor.orientation.getProperty();
        rotation.mul(m_delta);

        // Update associated Role.
        try {
            actor.orientation.push(actor);
        } catch (MleRuntimeException ex)
        {
            // ToDo: do we just ignore the fault?
        }
    }

    /* (non-Javadoc)
     * @see com.wizzer.mle.runtime.core.IMleObject#getProperty(java.lang.String)
     */
    public Object getProperty(String name) throws MleRuntimeException
    {
        if (name != null)
        {
            if (name.equals("position"))
                return position;
            else if (name.equals("orientation"))
                return orientation;
            else if (name.equals("scale"))
                 return scale;
            else if (name.equals("model"))
                return model;
            else if (name.equals("texture"))
                return texture;
        }

        // Specified name does not exist.
        throw new MleRuntimeException("MleModelActor: Unable to get property " + name + ".");
    }

    /* (non-Javadoc)
     * @see com.wizzer.mle.runtime.core.IMleObject#setProperty(java.lang.String, IMleProp)
     */
    public void setProperty(String name, IMleProp property)
            throws MleRuntimeException
    {
        if (name != null)
        {
            try
            {
                if (name.equals("texture"))
                {
                    // Read the data in from the input stream.
                    DataInputStream in = new DataInputStream(property.getStream());
                    byte[] data = new byte[property.getLength()];
                    in.readFully(data);

                    // Create a texture property and initialize it.
                    if (property.getType() == IMleProp.PROP_TYPE_MEDIAREF)
                    {
                        // Assume it's coming from the DPP and data is an index into
                        // the DPP Table-of-Contents.
                        Integer index = new Integer(new String(data));

                        // Retrieve the name from the DPP.
                        try
                        {
                            texture = new Mle3dTextureMapProperty();
                            MleMediaRef mref
                                    = MleDppLoader.getInstance().mleLoadMediaRef(index.intValue());
                            texture.setProperty(mref);
                        } catch (MleDppException ex)
                        {
                            throw new MleRuntimeException(ex.getMessage());
                        }

                        // Notify property change listeners.
                        notifyPropertyChange("texture", null, null);
                    } else
                    {
                        texture = new Mle3dTextureMapProperty();
                        MleTextureMapMediaRef mref = new MleTextureMapMediaRef();
                        mref.registerMedia(0,data.length,data);
                        texture.setProperty(mref);

                        // Notify property change listeners.
                        notifyPropertyChange("texture", null, null);
                    }

                    return;
                } else if (name.equals("model"))
                {
                    // Read the data in from the input stream.
                    DataInputStream in = new DataInputStream(property.getStream());
                    byte[] data = new byte[property.getLength()];
                    in.readFully(data);

                    // Create a model property and initialize it.
                    if (property.getType() == IMleProp.PROP_TYPE_MEDIAREF)
                    {
                        // Assume it's coming from the DPP and data is an index into
                        // the DPP Table-of-Contents.
                        Integer index = new Integer(new String(data));

                        // Retrieve the name from the DPP.
                        try
                        {
                            model = new Mle3dModelProperty();
                            MleMediaRef mref
                                    = MleDppLoader.getInstance().mleLoadMediaRef(index.intValue());
                            model.setProperty(mref);
                        } catch (MleDppException ex)
                        {
                            throw new MleRuntimeException(ex.getMessage());
                        }

                        // Notify property change listeners.
                        notifyPropertyChange("model", null, null);
                    } else
                    {
                        model = new Mle3dModelProperty();
                        MleModelMediaRef mref = new MleModelMediaRef();
                        mref.registerMedia(0,data.length,data);
                        model.setProperty(mref);

                        // Notify property change listeners.
                        notifyPropertyChange("model", null, null);
                    }

                    return;
                } else if (name.equals("position"))
                {
                    // Read the data in from the input stream.
                    DataInputStream in = new DataInputStream(property.getStream());
                    byte[] data = new byte[property.getLength()];
                    in.readFully(data);

                    // Create a translation property and initialize it.
                    position = new Mle3dTranslationProperty();
                    MlVector3 translation = new MlVector3();
                    // Expecting 3 floating-point values in stream.
                    MlMath.convertByteArrayToVector3(0, data, translation);
                    position.setProperty(translation);

                    // Notify property change listeners.
                    notifyPropertyChange("position", null, null);

                    return;
                } else if (name.equals("orientation"))
                {
                    // Read the data in from the input stream.
                    DataInputStream in = new DataInputStream(property.getStream());
                    byte[] data = new byte[property.getLength()];
                    in.readFully(data);

                    // Create a rotation property and initialize it.
                    orientation = new Mle3dQuaternionRotationProperty();
                    MlRotation rotation = new MlRotation();
                    // Expecting 4 floating-point values in stream.
                    MlMath.convertByteArrayToRotation(0, data, rotation);
                    orientation.setProperty(rotation);

                    // Notify property change listeners.
                    notifyPropertyChange("orientation", null, null);
                } else if (name.equals("scale"))
                {
                    // Read the data in from the input stream.
                    DataInputStream in = new DataInputStream(property.getStream());
                    byte[] data = new byte[property.getLength()];
                    in.readFully(data);

                    // Create a scale property and initialize it.
                    scale = new Mle3dNonuniformScaleProperty();
                    MlVector3 value = new MlVector3();
                    // Expecting 3 floating-point values in stream.
                    MlMath.convertByteArrayToVector3(0, data, value);
                    scale.setProperty(value);

                    // Notify property change listeners.
                    notifyPropertyChange("scale", null, null);
                }
            } catch (IOException ex)
            {
                throw new MleRuntimeException("MleModelActor: Unable to set property " + name + ".");
            }
        }

        // Specified name does not exist.
        throw new MleRuntimeException("MleModelActor: Unable to set property " + name + ".");
    }

    /* (non-Javadoc)
     * @see com.wizzer.mle.runtime.core.IMleObject#setPropertyArray(java.lang.String, int, int, java.io.ByteArrayInputStream)
     */
    public void setPropertyArray(String name, int length, int nElements, ByteArrayInputStream value) throws MleRuntimeException
    {
        throw new MleRuntimeException("MleModelActor: Unable to set property array " + name + ".");
    }
}

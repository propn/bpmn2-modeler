/*******************************************************************************
 * Copyright (c) 2011, 2012 Red Hat, Inc.
 *  All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 *
 * @author Bob Brodt
 ******************************************************************************/

package org.eclipse.bpmn2.modeler.core.adapters;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;

import org.eclipse.bpmn2.Choreography;
import org.eclipse.bpmn2.ChoreographyActivity;
import org.eclipse.bpmn2.ExtensionAttributeValue;
import org.eclipse.bpmn2.FlowElement;
import org.eclipse.bpmn2.Participant;
import org.eclipse.bpmn2.Process;
import org.eclipse.bpmn2.di.BPMNDiagram;
import org.eclipse.bpmn2.modeler.core.runtime.CustomTaskDescriptor;
import org.eclipse.bpmn2.modeler.core.runtime.TargetRuntime;
import org.eclipse.bpmn2.modeler.core.utils.JavaReflectionUtil;
import org.eclipse.bpmn2.modeler.core.utils.ModelUtil;
import org.eclipse.emf.common.notify.Adapter;
import org.eclipse.emf.common.notify.AdapterFactory;
import org.eclipse.emf.common.notify.Notifier;
import org.eclipse.emf.common.notify.impl.AdapterImpl;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.edit.domain.AdapterFactoryEditingDomain;
import org.eclipse.emf.edit.domain.EditingDomain;
import org.eclipse.emf.edit.domain.IEditingDomainProvider;
import org.eclipse.emf.edit.provider.ComposeableAdapterFactory;
import org.eclipse.emf.transaction.TransactionalEditingDomain;

/**
 * @author Bob Brodt
 *
 */
public class ExtendedPropertiesAdapter<T extends EObject> extends ResourceProvider {

	// common property keys
	public final static String LONG_DESCRIPTION = "long.description"; //$NON-NLS-1$
	public final static String UI_CAN_EDIT = "ui.can.edit"; //$NON-NLS-1$
	// Any adapter that uses this must override setValue() which understands
	// how to convert a String to the required type.
	// This is used in ComboObjectEditor (maybe others in the future)
	public final static String UI_CAN_EDIT_INLINE = "ui.can.edit.inline"; //$NON-NLS-1$
	public final static String UI_CAN_CREATE_NEW = "ui.can.create.new"; //$NON-NLS-1$
	// For Combo boxes (ComboObjectEditor), this indicates that an empty selection will be added to the list of possible choices;
	// For Text fields (TextObjectEditor), this indicates that the actual value of a feature should be used as the edit field text
	// instead of its textual representation as returned by @link ModelUtil#getDisplayName(). In this case, if the value is null,
	// it will be replaced with an empty string.
	public final static String UI_CAN_SET_NULL = "ui.can.set.null"; //$NON-NLS-1$
	public final static String UI_IS_MULTI_CHOICE = "ui.is.multi.choice"; //$NON-NLS-1$
	// the ObjectDescriptor object
	public static final String OBJECT_DESCRIPTOR = "object.descriptor"; //$NON-NLS-1$
	// the FeatureDescriptor object
	public static final String FEATURE_DESCRIPTOR = "feature.descriptor"; //$NON-NLS-1$
	// the EMF Resource that the object will eventually (or already does?) belong to
	public static final String RESOURCE = "resource"; //$NON-NLS-1$
	// Line number in XML document where this object is defined
	public static final String LINE_NUMBER = "line.number"; //$NON-NLS-1$
	
	protected static Hashtable<EClass,EObject> dummyObjects = new Hashtable<EClass,EObject>();

	protected Hashtable<
		EStructuralFeature, // feature ID
		Hashtable<String,Object>> // property key and value
			featureProperties = new Hashtable<EStructuralFeature, Hashtable<String,Object>>();
	protected Hashtable <
		String, // property key
		Object> // value
			objectProperties;
	
	protected AdapterFactory adapterFactory;
	
	@SuppressWarnings("rawtypes")
	public ExtendedPropertiesAdapter(AdapterFactory adapterFactory, T object) {
		super(object.eResource());
		this.adapterFactory = adapterFactory;
		setTarget(object);
	}
	
	@SuppressWarnings("rawtypes")
	public static ExtendedPropertiesAdapter adapt(Object object) {
		return adapt(object,null);
	}
	
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public static ExtendedPropertiesAdapter adapt(Object object, EStructuralFeature feature) {
		ExtendedPropertiesAdapter adapter = null;
		if (object instanceof ExtensionAttributeValue)
			object = ((ExtensionAttributeValue) object).eContainer();
		if (object instanceof EObject) {
			// If the EObject already has one of these adapters, find the "best" one for
			// the given feature. The "best" means the adapter will have defined a FeatureDescriptor
			// for the given feature.
			EObject eObject = (EObject) object;
			ExtendedPropertiesAdapter genericAdapter = null;
			for (Adapter a : eObject.eAdapters()) {
				if (a instanceof ExtendedPropertiesAdapter && ((ExtendedPropertiesAdapter)a).canAdapt(eObject, feature)) {
					if (a.getClass() == ExtendedPropertiesAdapter.class)
						genericAdapter = (ExtendedPropertiesAdapter) a;
					else
						adapter = (ExtendedPropertiesAdapter) a;
				}
			}
			// if no "best" adapter is found, use the generic adapter if one has been created for this EObject
			if (adapter==null && genericAdapter!=null)
				adapter = genericAdapter;
			
			EObject eclass = getFeatureClass(eObject,feature);
			if (adapter==null)
				adapter = (ExtendedPropertiesAdapter) AdapterUtil.adapt(eclass, ExtendedPropertiesAdapter.class);
			if (adapter!=null) {
				if (eObject instanceof EClass) {
					EObject dummy = getDummyObject((EClass)eObject);
					if (dummy!=null)
						eObject = dummy;
				}
				adapter.setTarget(eObject);
				adapter.getObjectDescriptor().setObject(eObject);
				if (feature!=null)
					adapter.getFeatureDescriptor(feature).setObject(eObject);
				
				// load the description for this object from Messages
				String description = getDescription(adapter.adapterFactory, eObject);
				if (description!=null && !description.isEmpty())
					adapter.setProperty(LONG_DESCRIPTION, description);
			}
		}
		return adapter;
	}

	/**
	 * Dummy objects are constructed when needed for an ExtendedPropertiesAdapter. The adapter factory
	 * (@see org.eclipse.bpmn2.modeler.ui.adapters.Bpmn2EditorItemProviderAdapterFactory) knows how to
	 * construct an ExtendedPropertiesAdapter from an EClass, however the adapter itself needs an EObject.
	 * This method constructs and caches these dummy objects as they are needed.
	 * 
	 * @param featureEType
	 * @return
	 */
	public static EObject getDummyObject(EClass eclass) {
		EObject object = dummyObjects.get(eclass);
		if (object==null && eclass.eContainer() instanceof EPackage && !eclass.isAbstract()) {
	    	EPackage pkg = (EPackage)eclass.eContainer();
	    	object = pkg.getEFactoryInstance().create(eclass);
			dummyObjects.put(eclass, object);
		}
		return object;
	}

	public AdapterFactory getAdapterFactory() {
		return adapterFactory;
	}
	
	public void setObjectDescriptor(ObjectDescriptor<T> od) {
		setProperty(OBJECT_DESCRIPTOR,od);
		od.setOwner(this);
	}

	private static EObject getFeatureClass(EObject object, EStructuralFeature feature) {
		EClass eclass = null;
		if (feature!=null && feature.eContainer() instanceof EClass) {
			eclass = (EClass)feature.eContainer();
		}
		if (eclass==null || eclass.isAbstract()) {
			return object;
		}
		return eclass;
	}

	@SuppressWarnings("unchecked")
	public ObjectDescriptor<T> getObjectDescriptor() {
		ObjectDescriptor<T> od = (ObjectDescriptor<T>) getProperty(OBJECT_DESCRIPTOR);
		if (od==null) {
			setObjectDescriptor(od = new ObjectDescriptor<T>(this, (T)getTarget()));
		}
		return od;
	}

	@SuppressWarnings("unchecked")
	public boolean hasFeatureDescriptor(EStructuralFeature feature) {
		FeatureDescriptor<T> fd = (FeatureDescriptor<T>) getProperty(feature,FEATURE_DESCRIPTOR);
		return fd!=null;
	}

	@SuppressWarnings("unchecked")
	public FeatureDescriptor<T> getFeatureDescriptor(EStructuralFeature feature) {
		FeatureDescriptor<T> fd = (FeatureDescriptor<T>) getProperty(feature,FEATURE_DESCRIPTOR);
		if (fd==null) {
			setFeatureDescriptor(feature, fd = new FeatureDescriptor<T>((T)getTarget(), feature));
		}
		return fd;
	}
	
	@SuppressWarnings("unchecked")
	public FeatureDescriptor<T> getFeatureDescriptor(String featureName) {
		EStructuralFeature feature = getFeature(featureName);
		return getFeatureDescriptor(feature);
	}
	
	public void setFeatureDescriptor(EStructuralFeature feature, FeatureDescriptor<T> fd) {
		Hashtable<String,Object> props = featureProperties.get(feature);
		if (props==null) {
			props = new Hashtable<String,Object>();
			featureProperties.put(feature,props);
		}
		fd.setOwner(this);
		props.put(FEATURE_DESCRIPTOR, fd);
	}

	/**
	 * Lookup method for the given feature name.
	 * 
	 * @param name
	 * @return
	 */
	public EStructuralFeature getFeature(String name) {
		EObject object = getObjectDescriptor().object;
		if (object instanceof ExtensionAttributeValue) {
			EObject container = ((ExtensionAttributeValue)object).eContainer();
			if (container!=null) {
				ExtendedPropertiesAdapter adapter = this.adapt(container);
				if (adapter!=null)
					return adapter.getFeature(name);
			}
		}
		for (Entry<EStructuralFeature, Hashtable<String, Object>> entry : featureProperties.entrySet()) {
			EStructuralFeature feature = entry.getKey();
			if (feature.getName().equals(name)) {
				return feature;
			}
		}
		return null;
	}

	public List<EStructuralFeature> getFeatures() {
		EObject object = getObjectDescriptor().object;
		if (object instanceof ExtensionAttributeValue) {
			EObject container = ((ExtensionAttributeValue)object).eContainer();
			if (container!=null) {
				ExtendedPropertiesAdapter adapter = this.adapt(container);
				if (adapter!=null)
					return adapter.getFeatures();
			}
		}
		List<EStructuralFeature> features = new ArrayList<EStructuralFeature>();
		features.addAll(featureProperties.keySet());
		return features;
	}

	private Hashtable <String, Object> getObjectProperties() {
		if (objectProperties==null)
			objectProperties = new Hashtable <String,Object>();
		return objectProperties;
	}
	
	public Object getProperty(String key) {
		return getObjectProperties().get(key);
	}

	public boolean getBooleanProperty(String key) {
		Object result = getProperty(key);
		if (result instanceof Boolean)
			return ((Boolean)result);
		return false;
	}

	public void setProperty(String key, Object value) {
		if (value==null)
			getObjectProperties().remove(key);
		else
			getObjectProperties().put(key, value);
	}

	public Object getProperty(EStructuralFeature feature, String key) {
		Hashtable<String,Object> props = featureProperties.get(feature);
		if (props==null) {
			props = new Hashtable<String,Object>();
			featureProperties.put(feature,props);
		}
		return props.get(key);
	}

	public boolean getBooleanProperty(EStructuralFeature feature, String key) {
		Object result = getProperty(feature, key);
		if (result instanceof Boolean)
			return ((Boolean)result);
		return false;
	}

	public void setProperty(EStructuralFeature feature, String key, Object value) {
		Hashtable<String,Object> props = featureProperties.get(feature);
		if (props==null) {
			props = new Hashtable<String,Object>();
			featureProperties.put(feature,props);
		}
		props.put(key, value);
	}

	public void setTarget(Notifier newTarget) {
		super.setTarget(newTarget);
		if (newTarget instanceof EObject && !(newTarget instanceof EClass)) {
			EObject object = (EObject)newTarget;
			for (Adapter a : object.eAdapters()) {
				if (a instanceof ExtendedPropertiesAdapter)
					return;
			}
			object.eAdapters().add(this);
		}
	}

	public boolean canAdapt(EObject object, EStructuralFeature feature) {
		if (object!=null) {
			if (getObjectDescriptor().object.eClass() == object.eClass()) {
				if (feature==null)
					return true;
				// only TRUE if this adapter already has a FeatureDescriptor for this feature 
				Hashtable<String,Object> props = featureProperties.get(feature);
				if (props!=null) {
					return true;
				}
			}
		}
		return false;
	}

	public static String getDescription(Object searchObject, EObject object) {
		String name = ""; //$NON-NLS-1$
		String description = ""; //$NON-NLS-1$
		if (object instanceof BPMNDiagram) {
			switch(ModelUtil.getDiagramType(object)) {
			case NONE:
				name = "UnknownDiagram"; //$NON-NLS-1$
				break;
			case PROCESS:
				name = "Process"; //$NON-NLS-1$
				break;
			case CHOREOGRAPHY:
				name = "Choreography"; //$NON-NLS-1$
				break;
			case COLLABORATION:
				name = "Collaboration"; //$NON-NLS-1$
				break;
			}
		}
		else if (object instanceof Participant) {
			Participant participant = (Participant) object;
			EObject container = participant.eContainer();
			if (container instanceof Choreography) {
				for (FlowElement fe : ((Choreography)container).getFlowElements()) {
					if (fe instanceof ChoreographyActivity) {
						ChoreographyActivity ca = (ChoreographyActivity) fe;
						if (ca.getParticipantRefs().contains(participant)) {
							name = "ParticipantBand"; //$NON-NLS-1$
							break;
						}
					}
				}
			}
		}
		// Get the model object's long description from the Messages class.
		// The field in Messages that contains the description will have the
		// form: "UI_<objectName>_long_description".
		// The Messages class must be contained somewhere in the package hierarchy
		// that contains the searchObject's class.
    	try {
    		if (name.isEmpty()) {
    			name = object.eClass().getName().replaceAll("Impl$", ""); //$NON-NLS-1$ //$NON-NLS-2$
    		}
    		if (description==null || description.isEmpty()) {
	        	String fieldName = "UI_" + name + "_long_description"; //$NON-NLS-1$ //$NON-NLS-2$
	        	Class messages = JavaReflectionUtil.findClass(searchObject, "Messages"); //$NON-NLS-1$
				Field field = messages.getField(fieldName);
				description = (String)field.get(null);
    		}
		} catch (Exception e) {
			// no biggie
		}
    	
    	return description;
	}

	public static String getDescription(Object searchObject, EObject object, EStructuralFeature feature) {
		String fieldName;
		Field field;
		String description = ""; //$NON-NLS-1$
		
		// Get the model feature's long description from the Messages class.
		// The field in Messages that contains the description will have the
		// form: "UI_<objectName>_<featureName>_description".
		// If that entry is not found, try looking for something in the form:
		// "UI_Any_<featureName>_description".
		// The Messages class must be contained somewhere in the package hierarchy
		// that contains the searchObject's class.
    	Class messages = JavaReflectionUtil.findClass(searchObject, "Messages"); //$NON-NLS-1$
		try {
			// fetch the description for this EClass and feature
    		fieldName = "UI_" + object.eClass().getName() + "_" + feature.getName() + "_description"; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
    		field = messages.getField(fieldName);
    		description += (String)field.get(null);
		}
		catch (Exception e) {
    		try {
    			// if a description is not found for this EClass, try "Any"
	    		fieldName = "UI_Any_" + feature.getName() + "_description"; //$NON-NLS-1$ //$NON-NLS-2$
	    		field = messages.getField(fieldName);
	    		description += (String)field.get(null);
    		}
    		catch (Exception e2) {
    		}
		}
		return description;
	}
	
	@SuppressWarnings("rawtypes")
	public static boolean compare(EObject thisObject, EObject otherObject, boolean similar) {
		for (EStructuralFeature f : thisObject.eClass().getEAllStructuralFeatures()) {
			// IDs are allowed to be different
			if (similar && "id".equals(f.getName())) //$NON-NLS-1$
				continue;
			Object v1 = otherObject.eGet(f);
			Object v2 = thisObject.eGet(f);
			// both null? equal!
			if (v1==null && v2==null)
				continue;
			// one or the other null? not equal!
			if (v1==null || v2==null)
				return false;
			// both not null? do a default compare...
			if (!v1.equals(v2)) {
				// the default Object.equals(obj) fails:
				// for Dynamic EObjects (used here as "proxies") only compare their proxy URIs 
				if (ModelUtil.isStringWrapper(v1) && ModelUtil.isStringWrapper(v2)) {
					v1 = ModelUtil.getStringWrapperValue(v1);
					v2 = ModelUtil.getStringWrapperValue(v2);
					if (v1==null && v2==null)
						continue;
					if (v1==null || v2==null)
						return false;
					if (v1.equals(v2))
						continue;
				}
				else if (v1 instanceof EObject && v2 instanceof EObject) {
					// for all other EObjects, do a deep compare...
					ExtendedPropertiesAdapter adapter = ExtendedPropertiesAdapter.adapt((EObject)v1);
					if (adapter!=null) {
						if (adapter.getObjectDescriptor().compare((EObject)v2,similar))
							continue;
					}
				}
				return false;
			}
		}
		return true;
	}

	public void setResource(Resource resource) {
		setProperty(RESOURCE, resource);
	}
	
	@Override
	public Resource getResource() {
		Resource resource = (Resource) getProperty(RESOURCE);
		if (resource==null) {
			ObjectDescriptor<T> od = (ObjectDescriptor<T>) getProperty(OBJECT_DESCRIPTOR);
			if (od!=null) {
				IResourceProvider rp = AdapterRegistry.INSTANCE.adapt(od.object.eContainer(), IResourceProvider.class);
				if (rp!=null && rp!=this)
					resource = rp.getResource();
			}
		}
		if (resource==null)
			return super.getResource();
		return resource;
	}
	

	public TransactionalEditingDomain getEditingDomain(Object context) {
		EditingDomain result = null;
		if (adapterFactory instanceof IEditingDomainProvider) {
			result = ((IEditingDomainProvider) adapterFactory).getEditingDomain();
		}
		if (result == null) {
			if (adapterFactory instanceof ComposeableAdapterFactory) {
				AdapterFactory rootAdapterFactory = ((ComposeableAdapterFactory) adapterFactory)
						.getRootAdapterFactory();
				if (rootAdapterFactory instanceof IEditingDomainProvider) {
					result = ((IEditingDomainProvider) rootAdapterFactory).getEditingDomain();
				}
			}
		}
		// it's gotta be a Transactional Editing Domain or nothing!
		if (result instanceof TransactionalEditingDomain)
			return (TransactionalEditingDomain)result;
		return null;
	}
}

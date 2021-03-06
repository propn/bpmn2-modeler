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
 * @author Ivar Meikas
 ******************************************************************************/
package org.eclipse.bpmn2.modeler.core.features.containers.lane;

import org.eclipse.bpmn2.Lane;
import org.eclipse.graphiti.features.IFeatureProvider;
import org.eclipse.graphiti.features.context.IDirectEditingContext;
import org.eclipse.graphiti.features.impl.AbstractDirectEditingFeature;
import org.eclipse.graphiti.mm.algorithms.GraphicsAlgorithm;
import org.eclipse.graphiti.mm.algorithms.Text;
import org.eclipse.graphiti.mm.pictograms.PictogramElement;
import org.eclipse.graphiti.mm.pictograms.Shape;

public class DirectEditLaneFeature extends AbstractDirectEditingFeature {

	public DirectEditLaneFeature(IFeatureProvider fp) {
	    super(fp);
    }

	@Override
    public int getEditingType() {
		return TYPE_TEXT;
    }

	@Override
    public String getInitialValue(IDirectEditingContext context) {
		PictogramElement pe = context.getPictogramElement();
		Lane lane = (Lane) getBusinessObjectForPictogramElement(pe);
		return lane.getName();
    }

	@Override
    public void setValue(String value, IDirectEditingContext context) {
		PictogramElement pe = context.getPictogramElement();
		Lane lane = (Lane) getBusinessObjectForPictogramElement(pe);
		lane.setName(value);
		updatePictogramElement(((Shape) pe).getContainer());
    }
	
	@Override
	public String checkValueValid(String value, IDirectEditingContext context) {
		if (value.length() < 1) {
			return Messages.DirectEditLaneFeature_Invalid_Empty;
		} else if (value.contains("\n")) { //$NON-NLS-1$
			return Messages.DirectEditLaneFeature_Invalid_Linebreak;
		}
		return null;
	}

	@Override
	public boolean canDirectEdit(IDirectEditingContext context) {
		PictogramElement pe = context.getPictogramElement();
		Object bo = getBusinessObjectForPictogramElement(pe);
		GraphicsAlgorithm ga = context.getGraphicsAlgorithm();
		return bo instanceof Lane && ga instanceof Text;
	}

	public boolean stretchFieldToFitText() {
		return true;
	}
}

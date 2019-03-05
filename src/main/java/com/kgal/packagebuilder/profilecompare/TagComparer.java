package com.kgal.packagebuilder.profilecompare;

import org.w3c.dom.Node;

public interface TagComparer {
	public boolean isIdentical (Node source, Node target);
}

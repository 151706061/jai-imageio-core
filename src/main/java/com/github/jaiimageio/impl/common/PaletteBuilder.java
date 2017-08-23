/*
 * $RCSfile: PaletteBuilder.java,v $
 *
 * Copyright (c) 2005 Sun Microsystems, Inc. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met: 
 * 
 * - Redistribution of source code must retain the above copyright 
 *   notice, this  list of conditions and the following disclaimer.
 * 
 * - Redistribution in binary form must reproduce the above copyright
 *   notice, this list of conditions and the following disclaimer in 
 *   the documentation and/or other materials provided with the
 *   distribution.
 * 
 * Neither the name of Sun Microsystems, Inc. or the names of 
 * contributors may be used to endorse or promote products derived 
 * from this software without specific prior written permission.
 * 
 * This software is provided "AS IS," without a warranty of any 
 * kind. ALL EXPRESS OR IMPLIED CONDITIONS, REPRESENTATIONS AND 
 * WARRANTIES, INCLUDING ANY IMPLIED WARRANTY OF MERCHANTABILITY, 
 * FITNESS FOR A PARTICULAR PURPOSE OR NON-INFRINGEMENT, ARE HEREBY
 * EXCLUDED. SUN MIDROSYSTEMS, INC. ("SUN") AND ITS LICENSORS SHALL 
 * NOT BE LIABLE FOR ANY DAMAGES SUFFERED BY LICENSEE AS A RESULT OF 
 * USING, MODIFYING OR DISTRIBUTING THIS SOFTWARE OR ITS
 * DERIVATIVES. IN NO EVENT WILL SUN OR ITS LICENSORS BE LIABLE FOR 
 * ANY LOST REVENUE, PROFIT OR DATA, OR FOR DIRECT, INDIRECT, SPECIAL,
 * CONSEQUENTIAL, INCIDENTAL OR PUNITIVE DAMAGES, HOWEVER CAUSED AND
 * REGARDLESS OF THE THEORY OF LIABILITY, ARISING OUT OF THE USE OF OR
 * INABILITY TO USE THIS SOFTWARE, EVEN IF SUN HAS BEEN ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGES. 
 * 
 * You acknowledge that this software is not designed or intended for 
 * use in the design, construction, operation or maintenance of any 
 * nuclear facility. 
 *
 * $Revision: 1.3 $
 * $Date: 2007/08/31 00:06:00 $
 * $State: Exp $
 */



package com.github.jaiimageio.impl.common;

import java.awt.Color;
import java.awt.Transparency;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.IndexColorModel;
import java.awt.image.Raster;
import java.awt.image.RenderedImage;
import java.awt.image.WritableRaster;

import javax.imageio.ImageTypeSpecifier;


/**
 * This class implements the octree quantization method 
 *  as it is described in the "Graphics Gems"
 *  (ISBN 0-12-286166-3, Chapter 4, pages 297-293)
 */
public class PaletteBuilder {

    /**
     * maximum of tree depth
     */
    protected static final int MAXLEVEL = 8;

    protected RenderedImage src;
    protected ColorModel srcColorModel;
    protected Raster srcRaster;

    protected int requiredSize;

    protected ColorNode root;

    protected int numNodes;
    protected int maxNodes;
    protected int currLevel;
    protected int currSize;

    protected ColorNode[] reduceList;
    protected ColorNode[] palette;

    protected int transparency;
    protected ColorNode transColor;


    /**
     * Creates an image representing given image
     * <code>src</code> using <code>IndexColorModel<code>.
     * 
     * Lossless conversion is not always possible (e.g. if number
     * of colors in the  given image exceeds maximum palette size).
     * Result image then is an approximation constructed by octree 
     * quantization method.
     *
     * @exception IllegalArgumentException if <code>src</code> is
     * <code>null</code>.
     *
     * @exception UnsupportedOperationException if implemented method
     * is unable to create approximation of <code>src</code>
     * and <code>canCreatePalette</code> returns <code>false</code>.
     *
     * @see #createIndexColorModel(RenderedImage)
     *
     * @see #canCreatePalette(RenderedImage)
     *
     */
    public static RenderedImage createIndexedImage(RenderedImage src) {
        PaletteBuilder pb = new PaletteBuilder(src);
        pb.buildPalette();
        return pb.getIndexedImage();
    }

    /**
     * Creates an palette representing colors from given image
     * <code>img</code>. If number of colors in the given image exceeds 
     * maximum palette size closest colors would be merged.
     *
     * @exception IllegalArgumentException if <code>img</code> is
     * <code>null</code>.
     *
     * @exception UnsupportedOperationException if implemented method
     * is unable to create approximation of <code>img</code>
     * and <code>canCreatePalette</code> returns <code>false</code>.
     *
     * @see #createIndexedImage(RenderedImage)
     *
     * @see #canCreatePalette(RenderedImage)
     *
     */
    public static IndexColorModel createIndexColorModel(RenderedImage img) {
        PaletteBuilder pb = new PaletteBuilder(img);
        pb.buildPalette();
        return pb.getIndexColorModel();
    }

    /**
     * Returns <code>true</code> if PaletteBuilder is able to create
     * palette for given image type.
     *
     * @param type an instance of <code>ImageTypeSpecifier</code> to be
     * indexed.
     *
     * @return <code>true</code> if the <code>PaletteBuilder</code>
     * is likely to be able to create palette for this image type.
     *
     * @exception IllegalArgumentException if <code>type</code>
     * is <code>null</code>.
     */
    public static boolean canCreatePalette(ImageTypeSpecifier type) {
	if (type == null) {
	    throw new IllegalArgumentException("type == null");
	}
        return true;
    }

    /**
     * Returns <code>true</code> if PaletteBuilder is able to create
     * palette for given rendered image.
     *
     * @param image an instance of <code>RenderedImage</code> to be
     * indexed.
     *
     * @return <code>true</code> if the <code>PaletteBuilder</code>
     * is likely to be able to create palette for this image type.
     *
     * @exception IllegalArgumentException if <code>image</code>
     * is <code>null</code>.
     */
    public static boolean canCreatePalette(RenderedImage image) {
	if (image == null) {
	    throw new IllegalArgumentException("image == null");
	}
	ImageTypeSpecifier type = new ImageTypeSpecifier(image);
	return canCreatePalette(type);
    }

    protected RenderedImage getIndexedImage() {
        IndexColorModel icm = getIndexColorModel();

        BufferedImage dst =
	    new BufferedImage(src.getWidth(), src.getHeight(),
			      BufferedImage.TYPE_BYTE_INDEXED, icm);

	WritableRaster wr = dst.getRaster();
        int minX = src.getMinX();
        int minY = src.getMinY();
	for (int y =0; y < dst.getHeight(); y++) {
	    for (int x = 0; x < dst.getWidth(); x++) {
		Color aColor = getSrcColor(x + minX, y + minY);
		wr.setSample(x, y, 0, findColorIndex(root, aColor));
	    }
	}
	
	return dst;
    }


    protected PaletteBuilder(RenderedImage src) {
        this(src, 256);
    }

    protected PaletteBuilder(RenderedImage src, int size) {
        this.src = src;
	this.srcColorModel = src.getColorModel();
	this.srcRaster = src.getData();

        this.transparency =
	    srcColorModel.getTransparency();

        if (transparency != Transparency.OPAQUE) {
            this.requiredSize = size - 1;
            transColor = new ColorNode();
            transColor.isLeaf = true;
        } else {
            this.requiredSize = size;
        }
    }

    private Color getSrcColor(int x, int y) {
	int argb = srcColorModel.getRGB(srcRaster.getDataElements(x, y, null));
	return new Color(argb, transparency != Transparency.OPAQUE);
    }

    protected int findColorIndex(ColorNode aNode, Color aColor) {
        if (transparency != Transparency.OPAQUE &&
	    aColor.getAlpha() != 0xff)
	{
            return 0; // default transparnt pixel
        }

        if (aNode.isLeaf) {
            return aNode.paletteIndex;
        } else {
            int childIndex = getBranchIndex(aColor, aNode.level);
	
            return findColorIndex(aNode.children[childIndex], aColor);
        }
    }

    protected void buildPalette() {
        reduceList = new ColorNode[MAXLEVEL + 1];
	for (int i = 0; i < reduceList.length; i++) {
	    reduceList[i] = null;
	}
	
	numNodes = 0;
	maxNodes = 0;
	root = null;
	currSize = 0;
	currLevel = MAXLEVEL;

        /*
          from the book

        */
	
        int w = src.getWidth();
        int h = src.getHeight();
        int minX = src.getMinX();
        int minY = src.getMinY();
	for (int y = 0; y < h; y++) {
	    for (int x = 0; x < w; x++) {

                Color aColor = getSrcColor(w - x + minX - 1, h - y + minY - 1);
                /*
                 * If transparency of given image is not opaque we assume all 
                 * colors with alpha less than 1.0 as fully transparent.
                 */
                if (transparency != Transparency.OPAQUE &&
		    aColor.getAlpha() != 0xff)
		{
                    transColor = insertNode(transColor, aColor, 0);
                } else {
                    root = insertNode(root, aColor, 0);
                }
                if (currSize > requiredSize) {
                    reduceTree();
                }
	    }
        }
    }

    protected ColorNode insertNode(ColorNode aNode, Color aColor, int aLevel) {

        if (aNode == null) {
	    aNode = new ColorNode();
	    numNodes++;
	    if (numNodes > maxNodes) {
		maxNodes = numNodes;
	    }
	    aNode.level = aLevel;
	    aNode.isLeaf = (aLevel > MAXLEVEL);
	    if (aNode.isLeaf) {
		currSize++;
	    }
	}
	aNode.colorCount++;
	aNode.red   += aColor.getRed();
	aNode.green += aColor.getGreen();
	aNode.blue  += aColor.getBlue();
	
	if (!aNode.isLeaf) {
	    int branchIndex = getBranchIndex(aColor, aLevel);
	    if (aNode.children[branchIndex] == null) {
		aNode.childCount++;
		if (aNode.childCount == 2) {
		    aNode.nextReducible = reduceList[aLevel];
		    reduceList[aLevel] = aNode;
		}
	    }
	    aNode.children[branchIndex] =
		insertNode(aNode.children[branchIndex], aColor, aLevel + 1);
	}
	return aNode;
    }

    protected IndexColorModel getIndexColorModel() {
        int size = currSize;
        if (transparency != Transparency.OPAQUE) {
            size ++; // we need place for transparent color;
        }

        byte[] red = new byte[size];
        byte[] green = new byte[size];
        byte[] blue = new byte[size];

        int index = 0;
        palette = new ColorNode[size];
        if (transparency != Transparency.OPAQUE) {
            index ++;
        }

        int lastIndex = findPaletteEntry(root, index, red, green, blue);

        IndexColorModel icm = null;
        if (transparency != Transparency.OPAQUE) {
            icm = new IndexColorModel(8, size, red, green, blue, 0);
        } else {
            icm = new IndexColorModel(8, currSize, red, green, blue);
        }
        return icm;
    }

    protected int findPaletteEntry(ColorNode aNode, int index,
				   byte[] red, byte[] green, byte[] blue)
        {
            if (aNode.isLeaf) {
                red[index]   = (byte)(aNode.red/aNode.colorCount);
                green[index] = (byte)(aNode.green/aNode.colorCount);
                blue[index]  = (byte)(aNode.blue/aNode.colorCount);
                aNode.paletteIndex = index;

                palette[index] = aNode;

                index++;
            } else {
                for (int i = 0; i < 8; i++) {
                    if (aNode.children[i] != null) {
                        index = findPaletteEntry(aNode.children[i], index,
                                                 red, green, blue);
                    }
                }
            }
            return index;
        }

    protected int getBranchIndex(Color aColor, int aLevel) {
        if (aLevel > MAXLEVEL || aLevel < 0) {
            throw new IllegalArgumentException("Invalid octree node depth: " +
					       aLevel);
        }

        int shift = MAXLEVEL - aLevel;
        int red_index = 0x1 & ((0xff & aColor.getRed()) >> shift);
        int green_index = 0x1 & ((0xff & aColor.getGreen()) >> shift);
        int blue_index = 0x1 & ((0xff & aColor.getBlue()) >> shift);
        int index = (red_index << 2) | (green_index << 1) | blue_index;
        return index;
    }

    protected void reduceTree() {
        int level = reduceList.length - 1;
	while (reduceList[level] == null && level >= 0) {
	    level--;
	}

        ColorNode thisNode = reduceList[level];
	if (thisNode == null) {
            // nothing to reduce
            return;
	}

        // look for element with lower color count
        ColorNode pList = thisNode;
        int minColorCount = pList.colorCount;

        int cnt = 1;
        while (pList.nextReducible != null) {
            if (minColorCount > pList.nextReducible.colorCount) {
                thisNode = pList;
                minColorCount = pList.colorCount;
            }
            pList = pList.nextReducible;
            cnt++;
        }

        // save pointer to first reducible node
        // NB: current color count for node could be changed in future
        if (thisNode == reduceList[level]) {
            reduceList[level] = thisNode.nextReducible;
        } else {
            pList = thisNode.nextReducible; // we need to process it
            thisNode.nextReducible = pList.nextReducible;
            thisNode = pList;
        }
	
        if (thisNode.isLeaf) {
            return;
        }

        // reduce node
        int leafChildCount = thisNode.getLeafChildCount();
        thisNode.isLeaf = true;
        currSize -= (leafChildCount - 1);
        int aDepth = thisNode.level;
        for (int i = 0; i < 8; i++) {
            thisNode.children[i] = freeTree(thisNode.children[i]);
        }
        thisNode.childCount = 0;
    }

    protected ColorNode freeTree(ColorNode aNode) {
	if (aNode == null) {
	    return null;
	}
	for (int i = 0; i < 8; i++) {
	    aNode.children[i] = freeTree(aNode.children[i]);
	}

        numNodes--;
	return null;
    }

    /**
     * The node of color tree.
     */
    protected class ColorNode {
        public boolean isLeaf;
        public int childCount;
        ColorNode[] children;

        public int colorCount;
        public long red;
        public long blue;
        public long green;

        public int paletteIndex;

        public int level;
        ColorNode nextReducible;

        public ColorNode() {
            isLeaf = false;
            level = 0;
            childCount = 0;
            children = new ColorNode[8];
            for (int i = 0; i < 8; i++) {
                children[i] = null;
            }

            colorCount = 0;
            red = green = blue = 0;

            paletteIndex = 0;
        }

        public int getLeafChildCount() {
            if (isLeaf) {
                return 0;
            }
            int cnt = 0;
            for (int i = 0; i < children.length; i++) {
                if (children[i] != null) {
                    if (children[i].isLeaf) {
                        cnt ++;
                    } else {
                        cnt += children[i].getLeafChildCount();
                    }
                }
            }
            return cnt;
        }

        public int getRGB() {
            int r = (int)red/colorCount;
            int g = (int)green/colorCount;
            int b = (int)blue/colorCount;

            int c = 0xff << 24 | (0xff&r) << 16 | (0xff&g) << 8 | (0xff&b);
            return c;
        }
    }
}

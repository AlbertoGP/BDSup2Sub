package deadbeef.SupTools;

import java.awt.image.BufferedImage;
import java.util.ArrayList;

import deadbeef.Tools.BitStream;
import deadbeef.Tools.FileBuffer;
import deadbeef.Tools.FileBufferException;
import deadbeef.Tools.ToolBox;

/*
 * Copyright 2009 Volker Oth (0xdeadbeef)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/**
 * Reading of HD-DVD captions demuxed from EVO transport streams (HD-DVD-SUP).
 *
 * @author 0xdeadbeef
 */
class SupHD implements Substream {

	/** ArrayList of captions contained in the current file  */
	final private ArrayList<SubPictureHD> subPictures;
	/** color palette of the last decoded caption  */
	private Palette palette;
	/** bitmap of the last decoded caption  */
	private Bitmap bitmap;
	/** FileBuffer to read from the file  */
	final private FileBuffer buffer;
	/** index of dominant color for the current caption  */
	private int primaryColorIndex;

	/**
	 * Constructor
	 * @param fname file name of SUP file to read
	 * @throws CoreException
	 */
	SupHD(final String fname) throws CoreException {
		int index = 0;
		try {
			buffer = new FileBuffer(fname);
		} catch (FileBufferException ex) {
			throw new CoreException(ex.getMessage());
		}
		int bufsize = (int)buffer.getSize();
		SubPictureHD pic = null;
		subPictures = new ArrayList<SubPictureHD>();
		try {
			while (index < bufsize) {
				if (Core.isCancelled())
					throw new CoreException("Cancelled by user!");
				Core.setProgress(index);

				if (buffer.getWord(index) != 0x5350)
					throw new CoreException("ID 'SP' missing at index "+ToolBox.hex(index,8)+"\n");
				int masterIndex = index+10; //end of header
				pic = new SubPictureHD();
				// hard code size since it's not part of the format???
				pic.width = 1920;
				pic.height = 1080;
				Core.printX("#"+(subPictures.size()+1)+"\n");
				pic.startTime = buffer.getDWordLE(index+=2); // read PTS
				int packetSize = buffer.getDWord(index+=10);
				// offset to command buffer
				int ofsCmd = buffer.getDWord(index+=4)+masterIndex;
				pic.imageBufferSize = ofsCmd - (index + 4);
				index  = ofsCmd;
				int dcsq = buffer.getWord(index);
				pic.startTime += (dcsq*1024);
				Core.printX("DCSQ start    ofs: "+ToolBox.hex(index,8)+"  ("+ToolBox.ptsToTimeStr(pic.startTime)+")\n");
				index+=2; // 2 bytes: dcsq
				int nextIndex = buffer.getDWord(index)+masterIndex; // offset to next dcsq
				index+=5;  // 4 bytes: offset, 1 byte: start
				int cmd = 0;
				boolean stopDisplay = false;
				boolean stopCommand = false;
				int alphaSum = 0;
				int minAlphaSum = 256*256; // 256 fully transparent entries
				while (!stopDisplay) {
					cmd = buffer.getByte(index++);
					switch (cmd) {
						case 0x01:
							Core.printX("DCSQ start    ofs: "+ToolBox.hex(index,8)+"  ("+ToolBox.ptsToTimeStr(pic.startTime+(dcsq*1024))+")\n");
							Core.printWarn("DCSQ start ignored due to missing DCSQ stop\n");
							break;
						case 0x02:
							stopDisplay = true;
							pic.endTime = pic.startTime +(dcsq*1024);
							Core.printX("DCSQ stop     ofs: "+ToolBox.hex(index,8)+"  ("+ToolBox.ptsToTimeStr(pic.endTime)+")\n");
							break;
						case 0x83: // palette
							Core.print("Palette info  ofs: "+ToolBox.hex(index,8)+"\n");
							pic.paletteOfs = index;
							index += 0x300;
							break;
						case 0x84: // alpha
							Core.print("Alpha info    ofs: "+ToolBox.hex(index,8)+"\n");
							alphaSum = 0;
							for (int i=index; i<index+0x100; i++)
								alphaSum += buffer.getByte(i);
							if (alphaSum < minAlphaSum) {
								pic.alphaOfs = index;
								minAlphaSum = alphaSum;
							} else
								Core.printWarn("Found faded alpha buffer -> alpha buffer skipped\n");

							index += 0x100;
							break;
						case 0x85: // area
							pic.setOfsX((buffer.getByte(index)<<4) | (buffer.getByte(index+1)>>4));
							pic.setImageWidth((((buffer.getByte(index+1)&0xf)<<8) | (buffer.getByte(index+2))) - pic.getOfsX() + 1);
							pic.setOfsY((buffer.getByte(index+3)<<4) | (buffer.getByte(index+4)>>4));
							pic.setImageHeight((((buffer.getByte(index+4)&0xf)<<8) | (buffer.getByte(index+5))) - pic.getOfsY() + 1);
							Core.print("Area info     ofs: "+ToolBox.hex(index,8)+"  ("
									+pic.getOfsX()+", "+pic.getOfsY()+") - ("+(pic.getOfsX()+pic.getImageWidth())+", "
									+(pic.getOfsY()+pic.getImageHeight())+")\n");
							index += 6;
							break;
						case 0x86: // even/odd offsets
							pic.imageBufferOfsEven = buffer.getDWord(index)+masterIndex;
							pic.imageBufferOfsOdd = buffer.getDWord(index+4)+masterIndex;
							Core.print("RLE buffers   ofs: "+ToolBox.hex(index,8)
									+"  (even: "+ToolBox.hex(pic.imageBufferOfsEven,8)
									+", odd: "+ToolBox.hex(pic.imageBufferOfsOdd,8)+"\n");
							index += 8;
							break;
						case 0xff:
							if (stopCommand) {
								Core.printWarn("DCSQ stop missing.\n");
								for (++index; index < bufsize; index++)
									if (buffer.getByte(index++) != 0xff) {
										index--;
										break;
									}
								stopDisplay = true;
							} else {
								index = nextIndex;
								// add to display time
								int d = buffer.getWord(index);
								dcsq = d;
								nextIndex = buffer.getDWord(index+2)+masterIndex;
								stopCommand = (index == nextIndex);
								Core.print("DCSQ          ofs: "+ToolBox.hex(index,8)+"  ("+(d*1024/90)+
										"ms),    next DCSQ at ofs: "+ToolBox.hex(nextIndex,8)+"\n");
								index += 6;
							}
							break;
						default:
							throw new CoreException("Unexpected command "+cmd+" at index "+ToolBox.hex(index,8));
					}
				}
				index = masterIndex + packetSize;
				subPictures.add(pic);
			}
		} catch (CoreException ex) {
			if (subPictures.size() == 0)
				throw ex;
			Core.printErr(ex.getMessage()+"\n");
			Core.print("Probably not all caption imported due to error.\n");
		} catch (FileBufferException ex) {
			if (subPictures.size() == 0)
				throw new CoreException (ex.getMessage());
			Core.printErr(ex.getMessage()+"\n");
			Core.print("Probably not all caption imported due to error.\n");
		}
	}

	/* (non-Javadoc)
	 * @see Substream#close()
	 */
	public void close() {
		if (buffer != null)
			buffer.close();
	}

	/**
	 * decode one line from the RLE buffer
	 * @param trg target buffer for uncompressed data
	 * @param trgOfs offset in target buffer
	 * @param width image width of encoded caption
	 * @param maxPixels maximum number of pixels in caption
	 * @param src source buffer
	 */
	private static void decodeLine(final byte trg[], int trgOfs, final int width, final int maxPixels, final BitStream src) {
		int x=0;
		int pixelsLeft = 0;
		int sumPixels = 0;
		boolean lf = false;

		while (src.bitsLeft() > 0 && sumPixels<maxPixels) {
			int rleType = src.readBits(1);
			int colorType = src.readBits(1);
			int color;
			int numPixels;

			if (colorType == 1)
				color = src.readBits(8);
			else
				color = src.readBits(2); // Colors between 0 and 3 are stored in two bits

			if (rleType == 1) {
				int rleSize = src.readBits(1);
				if (rleSize == 1) {
					numPixels = src.readBits(7) + 9;
					if (numPixels == 9)
						numPixels = width - x;
				} else {
					numPixels = src.readBits(3) + 2;
				}
			} else
				numPixels = 1;

			if (x+numPixels == width) {
				src.syncToByte();
				lf = true;
			}
			sumPixels += numPixels;

			// write pixels to target
			if (x+numPixels > width) {
				pixelsLeft = x+numPixels-width;
				numPixels = width-x;
				lf = true;
			} else
				pixelsLeft = 0;

			for (int i=0; i<numPixels; i++)
				trg[trgOfs+x+i] = (byte)color;

			if (lf==true) {
				trgOfs+=x+numPixels+width; // skip odd/even line
				x = pixelsLeft;
				lf = false;
			} else
				x += numPixels;

			// copy remaining pixels to new line
			for (int i=0; i<pixelsLeft; i++)
				trg[trgOfs+i] = (byte)color;
		}
	}

	/**
	 * decode caption from the input stream
	 * @param pic SubPicture object containing info about the caption
	 * @param transIdx index of the transparent color
	 * @return bitmap of the decoded caption
	 * @throws CoreException
	 */
	private Bitmap decodeImage(final SubPictureHD pic, final int transIdx) throws CoreException {
		final int w = pic.getImageWidth();
		final int h = pic.getImageHeight();
		int warnings = 0;

		if (w > pic.width || h > pic.height)
			throw new CoreException("Subpicture too large: "+w+"x"+h+
					" at offset "+ToolBox.hex(pic.imageBufferOfsEven, 8));

		final Bitmap bm = new Bitmap(w, h, (byte)transIdx);

		final int sizeEven = pic.imageBufferOfsOdd-pic.imageBufferOfsEven;
		final int sizeOdd = pic.imageBufferSize+pic.imageBufferOfsEven-pic.imageBufferOfsOdd;

		if (sizeEven <= 0 || sizeOdd <= 0)
			throw new CoreException("Corrupt buffer offset information");

		final byte evenBuf[] = new byte[sizeEven];
		final byte oddBuf[]  = new byte[sizeOdd];

		try {
			// copy buffers
			try {
				for (int i=0; i<evenBuf.length; i++)
					evenBuf[i] = (byte)buffer.getByte(pic.imageBufferOfsEven+i);
			} catch (ArrayIndexOutOfBoundsException ex) {
				warnings++;
			}
			try {
				for (int i=0; i<oddBuf.length; i++)
					oddBuf[i]  = (byte)buffer.getByte(pic.imageBufferOfsOdd+i);
			} catch (ArrayIndexOutOfBoundsException ex) {
				warnings++;
			}
			// decode even lines
			try {
				BitStream even = new BitStream(evenBuf);
				decodeLine(bm.getInternalBuffer(), 0, w, w*(h/2+(h&1)), even);
			} catch (ArrayIndexOutOfBoundsException ex) {
				warnings++;
			}
			// decode odd lines
			try {
				BitStream odd  = new BitStream(oddBuf);
				decodeLine(bm.getInternalBuffer(), w, w, (h/2)*w, odd);
			} catch (ArrayIndexOutOfBoundsException ex) {
				warnings++;
			}

			if (warnings > 0)
				Core.printWarn("problems during RLE decoding of picture at offset "+
						ToolBox.hex(pic.imageBufferOfsEven,8)+"\n");

			return bm;

		} catch (FileBufferException ex) {
			throw new CoreException (ex.getMessage());
		}
	}

	/**
	 * decode palette from the input stream
	 * @param pic SubPicture object containing info about the caption
	 * @return decoded palette
	 * @throws CoreException
	 */
	private Palette decodePalette(final SubPictureHD pic) throws CoreException {
		int ofs = pic.paletteOfs;
		int alphaOfs = pic.alphaOfs;

		Palette palette = new Palette(256);
		try {
			for (int i=0; i<palette.getSize(); i++) {
				// each palette entry consists of 3 bytes
				int y = buffer.getByte(ofs++);
				int cr,cb;
				if (Core.getSwapCrCb()) {
					cb = buffer.getByte(ofs++);
					cr = buffer.getByte(ofs++);
				} else {
					cr = buffer.getByte(ofs++);
					cb = buffer.getByte(ofs++);
				}
				// each alpha entry consists of 1 byte
				int alpha = 0xff - buffer.getByte(alphaOfs++);
				if (alpha < Core.getAlphaCrop()) // to not mess with scaling algorithms, make transparent color black
					palette.setRGB(i, 0, 0, 0);
				else
					palette.setYCbCr(i, y, cb, cr);
				palette.setAlpha(i, alpha);
			}
			return palette;
		} catch (FileBufferException ex) {
			throw new CoreException (ex.getMessage());
		}
	}

	/**
	 * decode given picture
	 * @param pic SubPicture object containing info about caption
	 * @throws CoreException
	 */
	private void decode(final SubPictureHD pic) throws CoreException {
		palette = decodePalette(pic);
		bitmap  = decodeImage(pic, palette.getIndexOfMostTransparentPaletteEntry());
		primaryColorIndex = bitmap.getPrimaryColorIndex(palette.getAlpha(), Core.getAlphaThr(), palette.getY());
	}

	/* (non-Javadoc)
	 * @see Substream#decode(int)
	 */
	public void decode(final int index) throws CoreException {
		if (index < subPictures.size())
			decode(subPictures.get(index));
		else
			throw new CoreException("Index "+index+" out of bounds\n");
	}

	/* setters / getters */

	/* (non-Javadoc)
	 * @see Substream#getPalette()
	 */
	public Palette getPalette() {
		return palette;
	}

	/* (non-Javadoc)
	 * @see Substream#getBitmap()
	 */
	public Bitmap getBitmap() {
		return bitmap;
	}

	/* (non-Javadoc)
	 * @see Substream#getImage()
	 */
	public BufferedImage getImage() {
		return bitmap.getImage(palette.getColorModel());
	}

	/* (non-Javadoc)
	 * @see Substream#getImage(Bitmap)
	 */
	public BufferedImage getImage(final Bitmap bm) {
		return bm.getImage(palette.getColorModel());
	}

	/* (non-Javadoc)
	 * @see Substream#getPrimaryColorIndex()
	 */
	public int getPrimaryColorIndex() {
		return primaryColorIndex;
	}

	/* (non-Javadoc)
	 * @see Substream#getSubPicture(int)
	 */
	public SubPicture getSubPicture(final int index) {
		return subPictures.get(index);
	}

	/* (non-Javadoc)
	 * @see Substream#getNumFrames()
	 */
	public int getNumFrames() {
		return subPictures.size();
	}

	/* (non-Javadoc)
	 * @see Substream#getNumForcedFrames()
	 */
	public int getNumForcedFrames() {
		return 0;
	}

	/* (non-Javadoc)
	 * @see Substream#isForced(int)
	 */
	public boolean isForced(final int index) {
		return false;
	}

	/* (non-Javadoc)
	 * @see Substream#getEndTime(int)
	 */
	public long getEndTime(final int index) {
		return subPictures.get(index).endTime;
	}

	/* (non-Javadoc)
	 * @see Substream#getStartTime(int)
	 */
	public long getStartTime(final int index) {
		return subPictures.get(index).startTime;
	}

	/* (non-Javadoc)
	 * @see Substream#getStartOffset(int)
	 */
	public long getStartOffset(final int index) {
		return subPictures.get(index).imageBufferOfsEven;
	}

}

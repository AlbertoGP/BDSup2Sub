package deadbeef.SupTools;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Iterator;

import deadbeef.Tools.FileBuffer;
import deadbeef.Tools.FileBufferException;
import deadbeef.Tools.QuantizeFilter;
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
 * Reading and writing of Blu-Ray captions demuxed from M2TS transport streams (BD-SUP).
 *
 * @author 0xdeadbeef
 */
class SupBD implements Substream {

	/** PGS composition state */
	private enum CompositionState {
		/** normal: doesn't have to be complete */
		NORMAL,
		/** acquisition point */
		ACQU_POINT,
		/** epoch start - clears the screen */
		EPOCH_START,
		/** epoch continue */
		EPOCH_CONTINUE,
		/** unknown value */
		INVALID
	}

	/** ArrayList of captions contained in the current file  */
	private final ArrayList<SubPictureBD> subPictures;
	/** color palette of the last decoded caption  */
	private Palette palette;
	/** bitmap of the last decoded caption  */
	private Bitmap bitmap;
	/** FileBuffer to read from the file  */
	private final FileBuffer buffer;
	/** index of dominant color for the current caption  */
	private int primaryColorIndex;
	/** number of forced captions in the current file  */
	private int numForcedFrames;

	private static  byte packetHeader[] = {
		0x50, 0x47,				// 0:  "PG"
		0x00, 0x00, 0x00, 0x00,	// 2:  PTS - presentation time stamp
		0x00, 0x00, 0x00, 0x00,	// 6:  DTS - decoding time stamp
		0x00,					// 10: segment_type
		0x00, 0x00,				// 11: segment_length (bytes following till next PG)
	};

	private static byte headerPCSStart[] = {
		0x00, 0x00, 0x00, 0x00,	// 0: video_width, video_height
		0x10, 					// 4: hi nibble: frame_rate (0x10=24p), lo nibble: reserved
		0x00, 0x00,				// 5: composition_number (increased by start and end header)
		(byte)0x80,				// 7: composition_state (0x80: epoch start)
		0x00,					// 8: palette_update_flag (0x80), 7bit reserved
		0x00,					// 9: palette_id_ref (0..7)
		0x01,					// 10: number_of_composition_objects (0..2)
		0x00, 0x00,				// 11: 16bit object_id_ref
		0x00,					// 13: window_id_ref (0..1)
		0x00,					// 14: object_cropped_flag: 0x80, forced_on_flag = 0x040, 6bit reserved
		0x00, 0x00, 0x00, 0x00	// 15: composition_object_horizontal_position, composition_object_vertical_position
	};

	private static byte headerPCSEnd[] = {
		0x00, 0x00, 0x00, 0x00,	// 0: video_width, video_height
		0x10,					// 4: hi nibble: frame_rate (0x10=24p), lo nibble: reserved
		0x00, 0x00,				// 5: composition_number (increased by start and end header)
		0x00,					// 7: composition_state (0x00: normal)
		0x00,					// 8: palette_update_flag (0x80), 7bit reserved
		0x00,					// 9: palette_id_ref (0..7)
		0x00,					// 10: number_of_composition_objects (0..2)
	};


	private static byte headerODSFirst[] = {
		0x00, 0x00,				// 0: object_id
		0x00,					// 2: object_version_number
		(byte)0xC0,				// 3: first_in_sequence (0x80), last_in_sequence (0x40), 6bits reserved
		0x00, 0x00, 0x00,		// 4: object_data_length - full RLE buffer length (including 4 bytes size info)
		0x00, 0x00, 0x00, 0x00,	// 7: object_width, object_height
	};

	private static byte headerODSNext[] = {
		0x00, 0x00,				// 0: object_id
		0x00,					// 2: object_version_number
		(byte)0x40,				// 3: first_in_sequence (0x80), last_in_sequence (0x40), 6bits reserved
	};

	private static byte headerWDS[] = {
		0x01,					// 0 : number of windows (currently assumed 1, 0..2 is legal)
		0x00,					// 1 : window id (0..1)
		0x00, 0x00, 0x00, 0x00,	// 2 : x-ofs, y-ofs
		0x00, 0x00, 0x00, 0x00	// 6 : width, height
	};


	/**
	 * Constructor
	 * @param fn file name of SUP file to read
	 * @throws CoreException
	 */
	SupBD(final String fn) throws CoreException {
		//int tFrame = (int)(90000/Core.getFPSSrc());
		int index = 0;
		try {
			buffer = new FileBuffer(fn);
		} catch (FileBufferException ex) {
			throw new CoreException(ex.getMessage());
		}
		final int bufsize = (int)buffer.getSize();
		SupSegment segment;
		SubPictureBD pic = null;
		SubPictureBD picLast = null;
		SubPictureBD picTmp = null;
		subPictures = new ArrayList<SubPictureBD>();
		int odsCtr = 0;
		int pdsCtr = 0;
		int odsCtrOld = 0;
		int pdsCtrOld = 0;
		int compNum = -1;
		int compNumOld = -1;
		int compCount = 0;
		long ptsPCS = 0;
		boolean paletteUpdate = false;
		CompositionState cs = CompositionState.INVALID;

		try {
			while (index < bufsize) {
				// for threaded version
				if (Core.isCancelled())
					throw new CoreException("Cancelled by user!");
				Core.setProgress(index);
				//
				segment = readSegment(index);
				String out;
				String so[] = new String[1]; // hack to return string
				switch (segment.type) {
					case 0x14:
						out = "PDS ofs:"+ToolBox.hex(index,8)+
						", size:"+ToolBox.hex(segment.size,4);
						if (compNum != compNumOld) {
							if (pic != null) {
								so[0] = null;
								int ps = parsePDS(segment, pic, so);
								if (ps >= 0) {
									Core.print(out+", "+so[0]+"\n");
									if (ps > 0) // don't count empty palettes
										pdsCtr++;
								} else {
									Core.print(out+"\n");
									Core.printWarn(so[0]+"\n");
								}
							} else {
								Core.print(out+"\n");
								Core.printWarn("missing PTS start -> ignored\n");
							}
						} else {
							Core.print(out+", comp # unchanged -> ignored\n");
						}
						break;
					case 0x15:
						out = "ODS ofs:"+ToolBox.hex(index,8)+
						", size:"+ToolBox.hex(segment.size,4);
						if (compNum != compNumOld) {
							if (!paletteUpdate) {
								if (pic != null) {
									so[0] = null;
									if (parseODS(segment, pic, so))
										odsCtr++;
									if (so[0] != null)
										out += ", "+so[0];
									Core.print(out+", img size: "+pic.getImageWidth()+"*"+pic.getImageHeight()+"\n");
								} else {
									Core.print(out+"\n");
									Core.printWarn("missing PTS start -> ignored\n");
								}
							} else {
								Core.print(out+"\n");
								Core.printWarn("palette update only -> ignored\n");
							}
						} else {
							Core.print(out+", comp # unchanged -> ignored\n");
						}
						break;
					case 0x16:
						compNum = getCompositionNumber(segment);
						cs = getCompositionState(segment);
						paletteUpdate = getPaletteUpdateFlag(segment);
						ptsPCS = segment.timestamp;
						if (segment.size >= 0x13)
							compCount = 1; // could be also 2, but we'll ignore this for the moment
						else
							compCount = 0;
						if (cs == CompositionState.INVALID) {
							Core.printWarn("Illegal composition state at offset "+ToolBox.hex(index,8)+"\n");
						} else if (cs == CompositionState.EPOCH_START) {
							// new frame
							if (subPictures.size() > 0 && (odsCtr==0 || pdsCtr==0)) {
								Core.printWarn("missing PDS/ODS: last epoch is discarded\n");
								subPictures.remove(subPictures.size()-1);
								compNumOld = compNum-1;
								if (subPictures.size() > 0)
									picLast = subPictures.get(subPictures.size()-1);
								else
									picLast = null;
							} else
								picLast = pic;
							pic = new SubPictureBD();
							subPictures.add(pic); // add to list
							pic.startTime = segment.timestamp;
							Core.printX("#> "+(subPictures.size())+" ("+ToolBox.ptsToTimeStr(pic.startTime)+")\n");

							so[0] = null;
							parsePCS(segment, pic, so);
							// fix end time stamp of previous pic if still missing
							if (picLast != null && picLast.endTime == 0)
								picLast.endTime = pic.startTime;

							out = "PCS ofs:"+ToolBox.hex(index,8)+
							", START, size:"+ToolBox.hex(segment.size,4)+
							", comp#: "+compNum+", forced: "+pic.isforced;
							if (so[0] != null)
								out+=", "+so[0]+"\n";
							else
								out+="\n";
							out += "PTS start: "+ToolBox.ptsToTimeStr(pic.startTime);
							out += ", screen size: "+pic.width+"*"+pic.height+"\n";
							odsCtr = 0;
							pdsCtr = 0;
							odsCtrOld = 0;
							pdsCtrOld = 0;
							picTmp = null;
							Core.print(out);
						} else {
							if (pic == null) {
								Core.printWarn("missing start of epoch at offset "+ToolBox.hex(index,8)+"\n");
								break;
							}
							out = "PCS ofs:"+ToolBox.hex(index,8)+", ";
							switch (cs) {
								case EPOCH_CONTINUE:
									out += "CONT, ";
									break;
								case ACQU_POINT:
									out += "ACQU, ";
									break;
								case NORMAL:
									out += "NORM, ";
									break;
							}
							out += " size: "+ToolBox.hex(segment.size,4)+
							", comp#: "+compNum+", forced: "+pic.isforced;;
							if (compNum != compNumOld) {
								so[0] = null;
								// store state to be able to revert to it
								picTmp = pic.deepCopy();
								picTmp.endTime = ptsPCS;
								// create new pic
								parsePCS(segment, pic, so);
							}
							if (so[0] != null)
								out+=", "+so[0];
							out += ", pal update: "+paletteUpdate+"\n";
							out += "PTS: "+ToolBox.ptsToTimeStr(segment.timestamp)+"\n";
							Core.print(out);
						}
						break;
					case 0x17:
						out = "WDS ofs:"+ToolBox.hex(index,8)+
						", size:"+ToolBox.hex(segment.size,4);
						if (pic != null) {
							parseWDS(segment, pic);
							Core.print(out+", dim: "+pic.winWidth+"*"+pic.winHeight+"\n");
						} else {
							Core.print(out+"\n");
							Core.printWarn("Missing PTS start -> ignored\n");
						}
						break;
					case 0x80:
						Core.print("END ofs:"+ToolBox.hex(index,8)+"\n");
						// decide whether to store this last composition section as caption or merge it
						if (cs == CompositionState.EPOCH_START) {
							if (compCount>0 && odsCtr>odsCtrOld && compNum!=compNumOld
									&& picMergable(picLast, pic)) {
								// the last start epoch did not contain any (new) content
								// and should be merged to the previous frame
								subPictures.remove(subPictures.size()-1);
								pic = picLast;
								if (subPictures.size() > 0)
									picLast = subPictures.get(subPictures.size()-1);
								else
									picLast = null;
								Core.printX("#< caption merged\n");
							}
						} else {
							long startTime = 0;
							if (pic != null) {
								startTime = pic.startTime;  // store
								pic.startTime = ptsPCS;    // set for testing merge
							}

							if (compCount>0 && odsCtr>odsCtrOld && compNum!=compNumOld
									&& !picMergable(picTmp, pic)) {
								// last PCS should be stored as separate caption
								if (odsCtr-odsCtrOld>1 || pdsCtr-pdsCtrOld>1)
									Core.printWarn("multiple PDS/ODS definitions: result may be erratic\n");
								// replace pic with picTmp (deepCopy created before new PCS)
								subPictures.set(subPictures.size()-1,picTmp); // replace in list
								picLast = picTmp;
								subPictures.add(pic); // add to list
								Core.printX("#< "+(subPictures.size())+" ("+ToolBox.ptsToTimeStr(pic.startTime)+")\n");
								odsCtrOld = odsCtr;

							} else {
								if (pic != null) {
									// merge with previous pic
									pic.startTime = startTime; // restore
									pic.endTime = ptsPCS;
									// for the unlikely case that forced flag changed during one captions
									if (picTmp != null && picTmp.isforced)
										pic.isforced = true;

									if (pdsCtr > pdsCtrOld || paletteUpdate)
										Core.printWarn("palette animation: result may be erratic\n");
								} else
									Core.printWarn("end without at least one epoch start\n");

							}
						}

						pdsCtrOld = pdsCtr;
						compNumOld = compNum;
						break;
					default:
						Core.printWarn("<unknown> "+ToolBox.hex(segment.type,2)+" ofs:"+ToolBox.hex(index,8)+"\n");
					break;
				}
				index += 13; // header size
				index += segment.size;
			}
		} catch (CoreException ex) {
			if (subPictures.size() == 0)
				throw ex;
			Core.printErr(ex.getMessage()+"\n");
			Core.print("Probably not all caption imported due to error.\n");
		}

		// check if last frame is valid
		if (subPictures.size() > 0 && (odsCtr==0 || pdsCtr==0)) {
			Core.printWarn("missing PDS/ODS: last epoch is discarded\n");
			subPictures.remove(subPictures.size()-1);
		}

		Core.setProgress(bufsize);
		// count forced frames
		numForcedFrames = 0;
		for (SubPictureBD p : subPictures) {
			if (p.isforced)
				numForcedFrames++;
		}
		Core.printX("\nDetected "+numForcedFrames+" forced captions.\n");
	}

	/**
	 * Checks if two SubPicture object can be merged because the time gap between them is rather small
	 * and the embedded objects seem to be identical
	 * @param a first SubPicture object (earlier)
	 * @param b 2nd SubPicture object (later)
	 * @return true if the SubPictures can be merged
	 */
	private static boolean picMergable(final SubPictureBD a, final SubPictureBD b) {
		boolean eq = false;
		if (a != null && b != null) {
			if (a.endTime == 0 || b.startTime-a.endTime < Core.getMergePTSdiff()) {
			ImageObject ao = a.getImgObj();
			ImageObject bo = b.getImgObj();
			if (ao != null && bo != null)
				if (ao.bufferSize == bo.bufferSize && ao.width == bo.width && ao.height == bo.height)
					eq = true;
			}
		}
		return eq;
	}

	/**
	 * Get ID for given frame rate
	 * @param fps frame rate
	 * @return byte ID for the given frame rate
	 */
	private static int getFpsId(final double fps) {
		if (fps == Core.FPS_24HZ)
			return 0x20;
		if (fps == Core.FPS_PAL)
			return 0x30;
		if (fps == Core.FPS_NTSC)
			return 0x40;
		if (fps == Core.FPS_PAL_I)
			return 0x60;
		if (fps == Core.FPS_NTSC_I)
			return 0x70;
		// assume FPS_24P (also for FPS_23_975)
		return 0x10;
	}

	/**
	 * Get frame rate for given byte ID
	 * @param id byte ID
	 * @return frame rate
	 */
	private static double getFpsFromID(final int id) {
		switch (id) {
			case 0x20:
				return Core.FPS_24HZ;
			case 0x30:
				return Core.FPS_PAL;
			case 0x40:
				return Core.FPS_NTSC;
			case 0x60:
				return Core.FPS_PAL_I;
			case 0x70:
				return Core.FPS_NTSC_I;
			default:
				return Core.FPS_24P; // assume FPS_24P (also for FPS_23_975)
		}
	}

	/**
	 * Create RLE buffer from bitmap
	 * @param bm bitmap to compress
	 * @return RLE buffer
	 */
	private static byte[] encodeImage(final Bitmap bm) {
		final ArrayList<Byte> bytes = new ArrayList<Byte>();
		byte color;
		int ofs;
		int len;
		//boolean eol;

		for (int y=0; y < bm.getHeight(); y++) {
			ofs = y*bm.getWidth();
			//eol = false;
			int x;
			for (x=0; x < bm.getWidth(); x+=len, ofs+=len) {
				color = bm.getInternalBuffer()[ofs];
				for (len=1; x+len < bm.getWidth(); len++)
					if (bm.getInternalBuffer()[ofs+len] != color)
						break;
				if (len<=2 && color != 0) {
					// only a single occurrence -> add color
					bytes.add(color);
					if (len==2)
						bytes.add(color);
				} else {
					if (len > 0x3fff)
						len = 0x3fff;
					bytes.add((byte)0); // rle id
					// commented out due to bug in SupRip
					/*if (color == 0 && x+len == bm.getWidth()) {
						bytes.add((byte)0);
						eol = true;
					} else*/
					if (color == 0 && len < 0x40){
						// 00 xx -> xx times 0
						bytes.add((byte)len);
					} else if (color == 0){
						// 00 4x xx -> xxx zeroes
						bytes.add((byte)(0x40|(len>>8)) );
						bytes.add((byte)len);
					} else if(len < 0x40) {
						// 00 8x cc -> x times value cc
						bytes.add((byte)(0x80|len) );
						bytes.add(color);
					} else {
						// 00 cx yy cc -> xyy times value cc
						bytes.add((byte)(0xc0|(len>>8)) );
						bytes.add((byte)len);
						bytes.add(color);
					}
				}
			}
			if (/*!eol &&*/ x == bm.getWidth()) {
				bytes.add((byte)0); // rle id
				bytes.add((byte)0);
			}
		}
		int size =  bytes.size();
		byte[] retval = new byte[size];
		Iterator<Byte> it = bytes.iterator();
		for (int i=0; i<size; i++)
			retval[i] = it.next();
		return retval;
	}

	/**
	 * Create the binary stream representation of one caption
	 * @param pic SubPicture object containing caption info
	 * @param bm bitmap
	 * @param pal palette
	 * @return byte buffer containing the binary stream representation of one caption
	 */
	static byte[] createSupFrame(final SubPicture pic, Bitmap bm, Palette pal) {
		// the last palette entry must be transparent
		if (pal.getSize() > 255 && pal.getAlpha(255) > 0) {
			// quantize image
			final QuantizeFilter qf = new QuantizeFilter();
			final Bitmap bmQ = new Bitmap(bm.getWidth(), bm.getHeight());
			int ct[] = qf.quantize(bm.toARGB(pal), bmQ.getInternalBuffer(), bm.getWidth(), bm.getHeight(), 255, false, false);
			int size = ct.length;
			if (size > 255) {
				size = 255;
				Core.print("Palette had to be reduced from "+pal.getSize()+" to "+size+" entries.\n");
				Core.printWarn("Quantizer failed.\n");
			} else
				Core.print("Palette had to be reduced from "+pal.getSize()+" to "+size+" entries.\n");
			// create palette
			pal = new Palette(size);
			for (int i=0; i<size; i++)
				pal.setARGB(i,ct[i]);
			// use new bitmap
			bm = bmQ;
		}

		byte rleBuf[] = encodeImage(bm);

		// for some obscure reason, a packet can be a maximum 0xfffc bytes
		// since 13 bytes are needed for the header("PG", PTS, DTS, ID, SIZE)
		// there are only 0xffef bytes available for the packet
		// since the first ODS packet needs an additional 11 bytes for info
		// and the following ODS packets need 4 additional bytes, the
		// first package can store only 0xffe4 RLE buffer bytes and the
		// following packets can store 0xffeb RLE buffer bytes
		int numAddPackets;
		if (rleBuf.length <= 0xffe4)
			numAddPackets = 0; // no additional packets needed;
		else
			numAddPackets = 1 + (rleBuf.length - 0xffe4)/0xffeb;

		// a typical frame consists of 8 packets. It can be enlonged by additional
		// object frames
		int palSize = bm.getHighestVisibleColorIndex(pal.getAlpha())+1;
		int size = packetHeader.length*(8+numAddPackets);
		size += headerPCSStart.length+headerPCSEnd.length;
		size += 2*headerWDS.length+headerODSFirst.length;
		size += numAddPackets*headerODSNext.length;
		size += (2+palSize*5) /* PDS */;
		size += rleBuf.length;

		int yOfs = pic.getOfsY() - Core.getCropOfsY();
		if (yOfs < 0)
			yOfs = 0;
		else {
			int yMax = pic.height - pic.getImageHeight() - 2*Core.getCropOfsY();
			if (yOfs > yMax)
				yOfs = yMax;
		}

		final int h = pic.height-2*Core.getCropOfsY();

		final byte buf[] = new byte[size];
		int index = 0;

		int fpsId = getFpsId(Core.getFPSTrg());

		/* time (in 90kHz resolution) needed to initialize (clear) the screen buffer
		   based on the composition pixel rate of 256e6 bit/s - always rounded up */
		int frameInitTime = (pic.width*pic.height*9+3199)/3200; // better use default height here
		/* time (in 90kHz resolution) needed to initialize (clear) the window area
		   based on the composition pixel rate of 256e6 bit/s - always rounded up
		   Note: no cropping etc. -> window size == image size */
		int windowInitTime = (bm.getWidth()*bm.getHeight()*9+3199)/3200;
		/* time (in 90kHz resolution) needed to decode the image
		   based on the decoding pixel rate of 128e6 bit/s - always rounded up  */
		int imageDecodeTime = (bm.getWidth()*bm.getHeight()*9+1599)/1600;
		// write PCS start
		packetHeader[10] = 0x16;											// ID
		int dts = (int)pic.startTime - (frameInitTime+windowInitTime);
		ToolBox.setDWord(packetHeader, 2, (int)pic.startTime);				// PTS
		ToolBox.setDWord(packetHeader, 6, dts);								// DTS
		ToolBox.setWord(packetHeader, 11, headerPCSStart.length);			// size
		for (int i=0; i<packetHeader.length; i++)
			buf[index++] = packetHeader[i];
		ToolBox.setWord(headerPCSStart,0, pic.width);
		ToolBox.setWord(headerPCSStart,2, h);								// cropped height
		ToolBox.setByte(headerPCSStart,4, fpsId);
		ToolBox.setWord(headerPCSStart,5, pic.compNum);
		headerPCSStart[14] = (pic.isforced ? (byte)0x40 : 0);
		ToolBox.setWord(headerPCSStart,15, pic.getOfsX());
		ToolBox.setWord(headerPCSStart,17, yOfs);
		for (int i=0; i<headerPCSStart.length; i++)
			buf[index++] = headerPCSStart[i];

		// write WDS
		packetHeader[10] = 0x17;											// ID
		int timeStamp = (int)pic.startTime - windowInitTime;
		ToolBox.setDWord(packetHeader, 2, timeStamp);						// PTS (keep DTS)
		ToolBox.setWord(packetHeader, 11, headerWDS.length);				// size
		for (int i=0; i<packetHeader.length; i++)
			buf[index++] = packetHeader[i];
		ToolBox.setWord(headerWDS, 2, pic.getOfsX());
		ToolBox.setWord(headerWDS, 4, yOfs);
		ToolBox.setWord(headerWDS, 6, bm.getWidth());
		ToolBox.setWord(headerWDS, 8, bm.getHeight());
		for (int i=0; i<headerWDS.length; i++)
			buf[index++] = headerWDS[i];

		// write PDS
		packetHeader[10] = 0x14;											// ID
		ToolBox.setDWord(packetHeader, 2, dts);								// PTS (=DTS of PCS/WDS)
		ToolBox.setDWord(packetHeader, 6, 0);								// DTS (0)
		ToolBox.setWord(packetHeader, 11, (2+palSize*5));					// size
		for (int i=0; i<packetHeader.length; i++)
			buf[index++] = packetHeader[i];
		buf[index++] = 0;
		buf[index++] = 0;
		for (int i=0; i<palSize; i++) {
			buf[index++] = (byte)i;											// index
			buf[index++] = pal.getY()[i];									// Y
			buf[index++] = pal.getCr()[i];									// Cr
			buf[index++] = pal.getCb()[i];									// Cb
			buf[index++] = pal.getAlpha()[i];								// Alpha
		}

		// write first OBJ
		int bufSize = rleBuf.length;
		int rleIndex = 0;
		if (bufSize > 0xffe4)
			bufSize = 0xffe4;
		packetHeader[10] = 0x15;											// ID
		timeStamp = dts+imageDecodeTime;
		ToolBox.setDWord(packetHeader, 2, timeStamp);						// PTS
		ToolBox.setDWord(packetHeader, 6, dts);								// DTS
		ToolBox.setWord(packetHeader, 11, headerODSFirst.length+bufSize);	// size
		for (int i=0; i<packetHeader.length; i++)
			buf[index++] = packetHeader[i];
		int marker = ((numAddPackets == 0) ? 0xC0000000 : 0x80000000);
		ToolBox.setDWord(headerODSFirst, 3, marker | (rleBuf.length+4));
		ToolBox.setWord(headerODSFirst, 7, bm.getWidth());
		ToolBox.setWord(headerODSFirst, 9, bm.getHeight());
		for (int i=0; i<headerODSFirst.length; i++)
			buf[index++] = headerODSFirst[i];
		for (int i=0; i<bufSize; i++)
			buf[index++] = rleBuf[rleIndex++];

		// write additional OBJ packets
		bufSize = rleBuf.length-bufSize; // remaining bytes to write
		for (int p=0; p<numAddPackets; p++) {
			int psize = bufSize;
			if (psize > 0xffeb)
				psize = 0xffeb;
			packetHeader[10] = 0x15;										// ID (keep DTS & PTS)
			ToolBox.setWord(packetHeader, 11, headerODSNext.length+psize);	// size
			for (int i=0; i<packetHeader.length; i++)
				buf[index++] = packetHeader[i];
			for (int i=0; i<headerODSNext.length; i++)
				buf[index++] = headerODSNext[i];
			for (int i=0; i<psize; i++)
				buf[index++] = rleBuf[rleIndex++];
			bufSize -= psize;
		}

		// write END
		packetHeader[10] = (byte)0x80;										// ID
		ToolBox.setDWord(packetHeader, 6, 0);								// DTS (0) (keep PTS of ODS)
		ToolBox.setWord(packetHeader, 11, 0);								// size
		for (int i=0; i<packetHeader.length; i++)
			buf[index++] = packetHeader[i];

		// write PCS end
		packetHeader[10] = 0x16;											// ID
		ToolBox.setDWord(packetHeader, 2, (int)pic.endTime);				// PTS
		dts = (int)pic.startTime - 1;
		ToolBox.setDWord(packetHeader, 6, dts);								// DTS
		ToolBox.setWord(packetHeader, 11, headerPCSEnd.length);				// size
		for (int i=0; i<packetHeader.length; i++)
			buf[index++] = packetHeader[i];
		ToolBox.setWord(headerPCSEnd,0, pic.width);
		ToolBox.setWord(headerPCSEnd,2, h);									// cropped height
		ToolBox.setByte(headerPCSEnd,4, fpsId);
		ToolBox.setWord(headerPCSEnd,5, pic.compNum+1);
		for (int i=0; i<headerPCSEnd.length; i++)
			buf[index++] = headerPCSEnd[i];

		// write WDS
		packetHeader[10] = 0x17;											// ID
		timeStamp = (int)pic.endTime - windowInitTime;
		ToolBox.setDWord(packetHeader, 2, timeStamp);						// PTS (keep DTS of PCS)
		ToolBox.setWord(packetHeader, 11, headerWDS.length);				// size
		for (int i=0; i<packetHeader.length; i++)
			buf[index++] = packetHeader[i];
		ToolBox.setWord(headerWDS, 2, pic.getOfsX());
		ToolBox.setWord(headerWDS, 4, yOfs);
		ToolBox.setWord(headerWDS, 6, bm.getWidth());
		ToolBox.setWord(headerWDS, 8, bm.getHeight());
		for (int i=0; i<headerWDS.length; i++)
			buf[index++] = headerWDS[i];

		// write END
		packetHeader[10] = (byte)0x80;										// ID
		ToolBox.setDWord(packetHeader, 2, dts);								// PTS (DTS of end PCS)
		ToolBox.setDWord(packetHeader, 6, 0);								// DTS (0)
		ToolBox.setWord(packetHeader, 11, 0);								// size
		for (int i=0; i<packetHeader.length; i++)
			buf[index++] = packetHeader[i];

		return buf;
	}

	/**
	 * read segment from the input stream
	 * @param offset offset inside the input stream
	 * @return SupSegment object containing info about the segment
	 * @throws CoreException
	 */
	private SupSegment readSegment(int offset) throws CoreException {
		try {
			final SupSegment segment = new SupSegment();
			if (buffer.getWord(offset) != 0x5047)
				throw new CoreException("PG missing at index "+ToolBox.hex(offset,8)+"\n");
			segment.timestamp = buffer.getDWord(offset+=2); // read PTS
			offset += 4; /* ignore DTS */
			segment.type = buffer.getByte(offset+=4);
			segment.size = buffer.getWord(++offset);
			segment.offset = offset+2;
			return segment;
		} catch (FileBufferException ex) {
			throw new CoreException (ex.getMessage());
		}
	}

	/**
	 * Retrieve composition state from PCS segment
	 * @param segment the segment to analyze
	 * @return CompositionState
	 * @throws CoreException
	 */
	private CompositionState getCompositionState(final SupSegment segment) throws CoreException {
		int type;
		try {
			type = buffer.getByte(segment.offset+7);
			switch (type) {
				case 0x00:
					return CompositionState.NORMAL;
				case 0x40:
					return CompositionState.ACQU_POINT;
				case 0x80:
					return CompositionState.EPOCH_START;
				case 0xC0:
					return CompositionState.EPOCH_CONTINUE;
				default:
					return CompositionState.INVALID;
			}
		} catch (FileBufferException ex) {
			throw new CoreException (ex.getMessage());
		}
	}

	/**
	 * Retrieve composition number from PCS segment
	 * @param segment the segment to analyze
	 * @return composition number as integer
	 * @throws CoreException
	 */
	private int getCompositionNumber(final SupSegment segment) throws CoreException {
		try {
			return buffer.getWord(segment.offset+5);
		} catch (FileBufferException ex) {
			throw new CoreException (ex.getMessage());
		}
	}

	/**
	 * Retrieve palette (only) update flag from PCS segment
	 * @param segment the segment to analyze
	 * @return true: this is only a palette update - ignore ODS
	 * @throws CoreException
	 */
	private boolean getPaletteUpdateFlag(final SupSegment segment) throws CoreException {
		try {
			return buffer.getByte(segment.offset+8) == 0x80;
		} catch (FileBufferException ex) {
			throw new CoreException (ex.getMessage());
		}
	}

	/**
	 * parse an PCS packet which contains width/height info
	 * @param segment object containing info about the current segment
	 * @param pic SubPicture object containing info about the current caption
	 * @param msg reference to message string
	 * @throws CoreException
	 */
	private void parsePCS(final SupSegment segment, final SubPictureBD pic, final String msg[]) throws CoreException {
		int index = segment.offset;
		try {
			if (segment.size >= 4) {
				pic.width  = buffer.getWord(index);			// video_width
				pic.height = buffer.getWord(index+2);		// video_height
				final int type = buffer.getByte(index+4);	// hi nibble: frame_rate, lo nibble: reserved
				final int num  = buffer.getWord(index+5); 	// composition_number
				// skipped:
				// 8bit  composition_state: 0x00: normal, 		0x40: acquisition point
				//							0x80: epoch start,	0xC0: epoch continue, 6bit reserved
				// 8bit  palette_update_flag (0x80), 7bit reserved
				final int palID = buffer.getByte(index+9);	// 8bit  palette_id_ref
				final int coNum = buffer.getByte(index+10);	// 8bit  number_of_composition_objects (0..2)
				if (coNum > 0) {
					// composition_object:
					int objID = buffer.getWord(index+11);	// 16bit object_id_ref
					msg[0] = "palID: "+palID+", objID: "+objID;
					if (pic.imageObjectList == null)
						pic.imageObjectList = new ArrayList<ImageObject>();
					ImageObject imgObj;
					if (objID >= pic.imageObjectList.size()) {
						imgObj = new ImageObject();
						pic.imageObjectList.add(imgObj);
					} else
						imgObj = pic.getImgObj(objID);
					imgObj.paletteID = palID;
					pic.objectID = objID;

					// skipped:  8bit  window_id_ref
					if (segment.size >= 0x13) {
						pic.type = type;
						// object_cropped_flag: 0x80, forced_on_flag = 0x040, 6bit reserved
						int forcedCropped = buffer.getByte(index+14);
						pic.compNum = num;
						pic.isforced = ( (forcedCropped & 0x40) == 0x40);
						imgObj.xOfs  = buffer.getWord(index+15);	// composition_object_horizontal_position
						imgObj.yOfs = buffer.getWord(index+17);		// composition_object_vertical_position
						// if (object_cropped_flag==1)
						// 		16bit object_cropping_horizontal_position
						//		16bit object_cropping_vertical_position
						//		16bit object_cropping_width
						//		object_cropping_height
					}
				}
			}

		} catch (FileBufferException ex) {
			throw new CoreException (ex.getMessage());
		}
	}

	/**
	 * parse an PCS packet which contains window info
	 * @param segment object containing info about the current segment
	 * @param pic SubPicture object containing info about the current caption
	 * @throws CoreException
	 */
	private void parseWDS(final SupSegment segment, final SubPictureBD pic) throws CoreException {
		final int index = segment.offset;
		try {
			if (segment.size >= 10) {
				// skipped:
				// 8bit: number of windows (currently assumed 1, 0..2 is legal)
				// 8bit: window id (0..1)
				pic.xWinOfs   = buffer.getWord(index+2);	// window_horizontal_position
				pic.yWinOfs   = buffer.getWord(index+4);	// window_vertical_position
				pic.winWidth  = buffer.getWord(index+6);	// window_width
				pic.winHeight = buffer.getWord(index+8);	// window_height
			}
		} catch (FileBufferException ex) {
			throw new CoreException (ex.getMessage());
		}
	}

	/**
	 * decode caption from the input stream
	 * @param pic SubPicture object containing info about the caption
	 * @param transIdx index of the transparent color
	 * @return bitmap of the decoded caption
	 * @throws CoreException
	 */
	private Bitmap decodeImage(final SubPictureBD pic, final int transIdx) throws CoreException {
		final int w = pic.getImageWidth();
		final int h = pic.getImageHeight();
		// always decode image obj 0, start with first entry in fragment list
		ImageObjectFragment info = pic.getImgObj().fragmentList.get(0);
		final long startOfs = info.imageBufferOfs;

		if (w > pic.width || h > pic.height)
			throw new CoreException("Subpicture too large: "+w+"x"+h+
					" at offset "+ToolBox.hex(startOfs, 8));

		final Bitmap bm = new Bitmap(w, h, (byte)transIdx);

		int b;
		int index = 0;
		int ofs = 0;
		int size = 0;
		int xpos = 0;

		try {
			// just for multi-packet support, copy all of the image data in one common buffer
			byte buf[] = new byte[pic.getImgObj().bufferSize];
			index = 0;
			for (int p = 0; p < pic.getImgObj().fragmentList.size(); p++) {
				// copy data of all packet to one common buffer
				info = pic.getImgObj().fragmentList.get(p);
				for (int i=0; i < info.imagePacketSize; i++) {
					buf[index+i] = (byte)buffer.getByte(info.imageBufferOfs+i);
				}
				index += info.imagePacketSize;
			}

			index = 0;

			do {
				b = buf[index++]&0xff;
				if (b == 0) {
					b = buf[index++]&0xff;
					if (b == 0) {
						// next line
						ofs = (ofs/w)*w;
						if (xpos < w)
							ofs+=w;
						xpos = 0;
					} else {
						if ( (b & 0xC0) == 0x40) {
							// 00 4x xx -> xxx zeroes
							size = ((b-0x40)<<8)+(buf[index++]&0xff);
							for (int i=0; i<size; i++)
								bm.getInternalBuffer()[ofs++] = 0; /*(byte)b;*/
							xpos += size;
						} else if ((b & 0xC0) == 0x80) {
							// 00 8x yy -> x times value y
							size = (b-0x80);
							b = buf[index++]&0xff;
							for (int i=0; i<size; i++)
								bm.getInternalBuffer()[ofs++] = (byte)b;
							xpos += size;
						} else if  ((b & 0xC0) != 0) {
							// 00 cx yy zz -> xyy times value z
							size = ((b-0xC0)<<8)+(buf[index++]&0xff);
							b = buf[index++]&0xff;
							for (int i=0; i<size; i++)
								bm.getInternalBuffer()[ofs++] = (byte)b;
							xpos += size;
						}  else {
							// 00 xx -> xx times 0
							for (int i=0; i<b; i++)
								bm.getInternalBuffer()[ofs++] = 0;
							xpos += b;
						}
					}
				} else {
					bm.getInternalBuffer()[ofs++] = (byte)b;
					xpos++;
				}
			} while (index < buf.length);

			return bm;
		} catch (FileBufferException ex) {
			throw new CoreException (ex.getMessage());
		} catch (ArrayIndexOutOfBoundsException ex) {
			Core.printWarn("problems during RLE decoding of picture OBJ at offset "+
					ToolBox.hex(startOfs+index,8)+"\n");
			return bm;
		}
	}

	/**
	 * parse an ODS packet which contain the image data
	 * @param segment object containing info about the current segment
	 * @param pic SubPicture object containing info about the current caption
	 * @param msg reference to message string
	 * @return true if this is a valid new object (neither invalid nor a fragment)
	 * @throws CoreException
	 */
	private boolean parseODS(final SupSegment segment, final SubPictureBD pic, final String msg[]) throws CoreException {
		final int index = segment.offset;
		ImageObjectFragment info;
		try {
			final int objID = buffer.getWord(index);		// 16bit object_id
			final int objVer = buffer.getByte(index+1);		// 16bit object_id
			final int objSeq = buffer.getByte(index+3);		// 8bit  first_in_sequence (0x80),
															// last_in_sequence (0x40), 6bits reserved
			final boolean first = (objSeq & 0x80) == 0x80;
			final boolean last  = (objSeq & 0x40) == 0x40;

			if (pic.imageObjectList == null)
				pic.imageObjectList = new ArrayList<ImageObject>();
			ImageObject imgObj;
			if (objID >= pic.imageObjectList.size()) {
				imgObj = new ImageObject();
				pic.imageObjectList.add(imgObj);
			} else
				imgObj = pic.getImgObj(objID);

			if (imgObj.fragmentList == null || first) {			// 8bit  object_version_number
				// skipped:
				//  24bit object_data_length - full RLE buffer length (including 4 bytes size info)
				final int width  = buffer.getWord(index+7);		// object_width
				final int height = buffer.getWord(index+9);		// object_height

				if (width <= pic.width && height <= pic.height) {
					imgObj.fragmentList = new ArrayList<ImageObjectFragment>();
					info = new ImageObjectFragment();
					info.imageBufferOfs = index+11;
					info.imagePacketSize = segment.size - (index+11-segment.offset);
					imgObj.fragmentList.add(info);
					imgObj.bufferSize = info.imagePacketSize;
					imgObj.height = height;
					imgObj.width  = width;
					msg[0] = "ID: "+objID+", update: "+objVer+", seq: "+(first?"first":"")+
						((first&&last)?"/":"")+(last?"" + "last":"");
					return true;
				} else {
					Core.printWarn("Invalid image size - ignored\n");
					return false;
				}
			} else {
				// object_data_fragment
				// skipped:
				//  16bit object_id
				//  8bit  object_version_number
				//  8bit  first_in_sequence (0x80), last_in_sequence (0x40), 6bits reserved
				info = new ImageObjectFragment();
				info.imageBufferOfs = index+4;
				info.imagePacketSize = segment.size - (index+4-segment.offset);
				imgObj.fragmentList.add(info);
				imgObj.bufferSize += info.imagePacketSize;
				msg[0] = "ID: "+objID+", update: "+objVer+", seq: "+(first?"first":"")+
					((first&&last)?"/":"")+(last?"" + "last":"");
				return false;
			}

		} catch (FileBufferException ex) {
			throw new CoreException (ex.getMessage());
		}
	}

	/**
	 * decode palette from the input stream
	 * @param pic SubPicture object containing info about the current caption
	 * @return
	 * @throws CoreException
	 */
	private Palette decodePalette(final SubPictureBD pic) throws CoreException {
		boolean fadeOut = false;
		int palIndex = 0;
		ArrayList<PaletteInfo> pl = pic.palettes.get(pic.getImgObj().paletteID);
		if (pl == null)
			throw new CoreException("Palette ID out of bounds.");

		final Palette palette = new Palette(256, Core.usesBT601());
		// by definition, index 0xff is always completely transparent
		// also all entries must be fully transparent after initialization

		try {
			for (int j=0; j<pl.size(); j++) {
				PaletteInfo p = pl.get(j);
				int index = p.paletteOfs;
				for (int i=0; i<p.paletteSize; i++) {
					// each palette entry consists of 5 bytes
					palIndex = buffer.getByte(index);
					int y = buffer.getByte(++index);
					int cr,cb;
					if (Core.getSwapCrCb()) {
						cb = buffer.getByte(++index);
						cr = buffer.getByte(++index);
					} else {
						cr = buffer.getByte(++index);
						cb = buffer.getByte(++index);
					}
					int alpha = buffer.getByte(++index);

					int alphaOld = palette.getAlpha(palIndex);
					// avoid fading out
					if (alpha >= alphaOld) {
						if (alpha < Core.getAlphaCrop()) {// to not mess with scaling algorithms, make transparent color black
							y = 16;
							cr = 128;
							cb = 128;
						}
						palette.setAlpha(palIndex, alpha);
					} else fadeOut = true;

					palette.setYCbCr(palIndex, y, cb, cr);
					index++;
				}
			}
			if (fadeOut)
				Core.printWarn("fade out detected -> patched palette\n");
			return palette;
		} catch (FileBufferException ex) {
			throw new CoreException (ex.getMessage());
		}
	}

	/**
	 * parse an PDS packet which contain palette info
	 * @param segment object containing info about the current segment
	 * @param pic SubPicture object containing info about the current caption
	 * @param msg reference to message string
	 * @throws CoreException
	 * @throws FileBufferException
	 * @throws CoreException
	 * @returns number of valid palette entries (-1 for fault)
	 */
	private int parsePDS(final SupSegment segment, final SubPictureBD pic, final String msg[]) throws CoreException {
		final int index = segment.offset;
		try {
			final int paletteID = buffer.getByte(index);	// 8bit palette ID (0..7)
			// 8bit palette version number (incremented for each palette change)
			final int paletteUpdate = buffer.getByte(index+1);
			if (pic.palettes == null) {
				pic.palettes = new ArrayList<ArrayList <PaletteInfo>>();
				for (int i=0; i<8; i++)
					pic.palettes.add(new ArrayList<PaletteInfo>());
			}
			if (paletteID > 7) {
				msg[0] = "Illegal palette id at offset "+ToolBox.hex(index, 8);
				return -1;
			}
			//
			ArrayList<PaletteInfo> al = pic.palettes.get(paletteID);
			if (al == null)
				al = new ArrayList<PaletteInfo>();
			final PaletteInfo p = new PaletteInfo();
			p.paletteSize = (segment.size-2)/5;
			p.paletteOfs = index+2;
			al.add(p);
			msg[0] = "ID: "+paletteID+", update: "+paletteUpdate+", "+p.paletteSize+" entries";
			return p.paletteSize;
		} catch (FileBufferException ex) {
			throw new CoreException (ex.getMessage());
		}
	}

	/**
	 * decode given picture
	 * @param pic SubPicture object containing info about caption
	 * @throws CoreException
	 */
	private void decode(final SubPictureBD pic)  throws CoreException {
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
		return numForcedFrames;
	}

	/* (non-Javadoc)
	 * @see Substream#close()
	 */
	public void close() {
		if (buffer != null)
			buffer.close();
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
	 * @see Substream#isForced(int)
	 */
	public boolean isForced(final int index) {
		return subPictures.get(index).isforced;
	}

	/* (non-Javadoc)
	 * @see Substream#getStartOffset(int)
	 */
	public long getStartOffset(final int index) {
		SubPictureBD pic = subPictures.get(index);
		return pic.getImgObj().fragmentList.get(0).imageBufferOfs;
	}

	/**
	 * Get frame rate for given caption
	 * @param index index of caption
	 * @return frame rate
	 */
	double getFps(final int index) {
		return getFpsFromID(subPictures.get(index).type);
	}
}

class SupSegment {
	/** type of segment (segment_type) */
	int   type;
	/** segment size in bytes (segment_length) */
	int   size;
	/** segment PTS time stamp */
	long  timestamp;
	/** file offset of segment */
	int   offset;
}


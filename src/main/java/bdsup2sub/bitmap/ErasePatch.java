/*
 * Copyright 2012 Volker Oth (0xdeadbeef) / Miklos Juhasz (mjuhasz)
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
package bdsup2sub.bitmap;

/**
 * Storage class holding information to erase a rectangular part of the caption.
 */
public class ErasePatch {

    /** X coordinate of patch */
    public final int x;
    /** Y coordinate of patch */
    public final int y;
    /** Width of patch */
    public final int width;
    /** Height of patch */
    public final int height;

    /**
     * @param x X coordinate of patch
     * @param y Y coordinate of patch
     * @param width Width of patch
     * @param height Height of patch
     */
    public ErasePatch(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }
}

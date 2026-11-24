/*
 *     Arbor: Procedural Voxel Tree Generation.
 *     Copyright (C) 2026  Jeff Chen
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.jeffch.arbor.viewer;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Human names for preset settings. The same JSON key can mean different things, so names go by parent and key.
 */
final class SettingNames {
  private static final String TABLE = """
          tree.sizeScale=Size scale
          tree.sizeVariance=Size variance per tree
          tree.maxHeight=Maximum height
          tree.sink=Sink into ground
          tree.roll=Tree rotation
          tree.trunkSpread=Trunk spread
          tree.groundNormal=Ground slope normal
          tree.slopeRecovery=Trunk slope recovery
          shapeControl.style=Crown shape style
          shapeControl.crownBase=Crown base height
          shapeControl.radius=Crown radius by height
          shapeControl.action=At the crown edge
          shapeControl.steer=Steer along crown edge
          simple.height=Height
          simple.crownWidth=Crown width
          simple.trunkThickness=Trunk thickness
          simple.branchDensity=Branch density
          simple.branchAngle=Branch angle
          simple.droop=Droop
          simple.twist=Twist
          simple.leafFullness=Leaf fullness
          simple.windLean=Wind lean
          simple.sizeVariety=Size variety
          forces.name=Force name
          forces.type=Force type
          forces.vector=Direction
          forces.position=Attractor position
          forces.strength=Strength
          forces.falloff=Distance falloff
          forces.directionVariance=Wind direction variance
          forces.gust=Gust strength
          forces.gustSize=Gust size
          forces.heightPower=Wind increase with height
          forces.flexibility=Thin branch flexibility
          shapes.fullRadius=Full block radius
          shapes.wallRadius=Wall radius
          shapes.wallGapChance=Wall gap chance
          shapes.thinCoverage=Thin branch coverage
          shapes.gateChance=Fence gate chance
          shapes.gateInWallChance=Lowered gate chance
          shapes.openGateChance=Open gate at splits
          shapes.impliedRadius=Implied branch radius
          shapes.impliedLeafSize=Implied leaf size
          shapes.impliedLeafStrength=Implied leaf strength
          shapes.deadBushChance=Dead bush chance
          shapes.deadBushDensity=Dead bush crowding
          shapes.deadBushOffFenceChance=Dead bush replaces fence
          shapes.smoothTrunk=Smooth trunk
          shapes.smoothStairDepth=Stair smoothing depth
          shapes.smoothSlabDepth=Slab smoothing depth
          shapes.smoothWallDepth=Wall smoothing depth
          shapes.smoothFenceDepth=Fence smoothing depth
          shapes.buttonChance=Trunk button chance
          shapes.buttonSpacing=Trunk button spacing
          shapes.tipCanopyDensity=Bare tip trim density
          shapes.fineBranchChance=Fine branch hint chance
          shapes.fineGateChance=Fine branch gate chance
          shapes.trunkFullRadius=Full log trunk radius
          shapes.saplingLeafDensity=Sapling leaf density
          shapes.voxelRandomness=Voxel randomness
          shapes.leafBlockDensity=Full leaf block density
          shapes.trimToCanopy=Trim wood to canopy
          shapes.removeDetached=Remove floating blocks
          shapes.mode=Voxelizer mode
          blocks.log=Logs
          blocks.junction=Six-sided wood
          blocks.cutEnd=Cut ends
          blocks.planks=Planks
          blocks.stairs=Stairs
          blocks.slab=Slabs
          blocks.wall=Walls
          blocks.fence=Fences
          blocks.gate=Fence gates
          blocks.button=Buttons
          blocks.sapling=Saplings
          blocks.deadBush=Dead bushes
          blocks.leaves=Leaves
          plantLayers.name=Layer name
          plantLayers.enabled=Enabled
          plantLayers.treeChance=Chance per tree
          plantLayers.blocks=Plants
          plantLayers.chance=Chance per block
          plantLayers.leafDensity=Leaf density range
          plantLayers.height=Height range
          plantLayers.side=Crown side
          plantLayers.nearBranch=Near branch level
          plantLayers.nearDistance=Distance from branch
          plantLayers.clumpSize=Clump size
          plantLayers.doubleHalf=Double plant halves
          pinecones.enabled=Enabled
          pinecones.chance=Chance per spot
          pinecones.undersideShare=Underside share
          pinecones.height=Height range
          pinecones.minLevel=Lowest branch level
          pinecones.blocks=Pinecone blocks
          vines.enabled=Enabled
          vines.chance=Chance per spot
          vines.minLength=Shortest strand
          vines.maxLength=Longest strand
          vines.blocks=Vine blocks
          moss.enabled=Enabled
          moss.amount=Amount
          moss.patchSize=Patch size
          moss.upward=Prefer upward faces
          moss.height=Height range
          moss.blocks=Moss blocks
          levels.name=Level name
          levels.parent=Parent level
          levels.segmentLength=Segment length
          levels.wood=Draw as wood
          generation.mode=Placement mode
          generation.first=First position
          generation.last=Last position
          generation.clip=Clip to range
          generation.rotation=Rotation
          generation.position=Position offset
          generation.sink=Sink into parent
          generation.pruning=Pruning curve
          generation.knockout=Random removal
          generation.extendParent=Extra tip children
          generation.count=Count
          generation.density=Count per block
          generation.frequency=Group spacing
          generation.phyllotaxy=Phyllotaxy pattern
          generation.childrenPerNode=Children per node
          generation.internodeLength=Internode length
          generation.spread=Position spread
          generation.spiral=Spiral angle
          generation.limit=Group limit
          generation.align=Align to sky
          generation.balance=Group balance
          generation.threshold=Bend threshold
          generation.spacing=Bend spacing
          generation.locality=Bend preference
          length.absolute=Length in blocks
          length.percentOfParent=Fraction of parent length
          length.percentOfRadius=Multiple of radius
          length.min=Minimum length
          length.max=Maximum length
          length.shapeScale=Crown fit scale
          length.stopShapeControl=Ignore crown shape
          orientation.startAngle=Start angle
          orientation.startRoll=Start roll
          orientation.alignment=Align frame to sky
          shape.gravity=Gravity
          shape.ancestor=Pull toward parent
          shape.straightness=Straightness
          shape.twist=Twist
          shape.curlUp=Curl along parent
          shape.curlRight=Curl sideways
          shape.curlOut=Curl outward
          shape.zigzag=Zigzag angle
          shape.zigzagLength=Zigzag length
          noise.earlyAmount=Growth noise
          noise.earlyTurbulence=Growth noise detail
          noise.earlyThreshold=Growth noise threshold
          noise.lateAmount=Offset noise
          noise.lateTurbulence=Offset noise detail
          noise.lateSmooth=Offset noise smoothing
          jink.amount=Jink amount
          jink.segments=Jink segments
          jink.uniformity=Jink uniformity
          path.amount=Wave size
          path.twist=Wave twist
          path.length=Wave length
          path.offset=Wave phase
          break.chance=Break chance
          break.spot=Break position
          prune.interior=Prune inward branches
          prune.up=Prune upward branches
          prune.down=Prune downward branches
          prune.general=Prune at random
          prune.ground=Prune below ground
          radius.absolute=Radius in blocks
          radius.percentOfParent=Fraction of parent radius
          radius.taper=Taper curve
          radius.clampToParent=Limit to parent radius
          radius.min=Minimum radius
          radius.max=Maximum radius
          radius.lengthCompensation=Taper compensation
          radius.lengthKillRatio=Maximum length to radius
          flares.radius=Flare radius
          flares.height=Flare height
          flares.pinch=Flare pinch
          flares.lobes=Flare lobes
          flares.noise=Flare noise
          flares.angle=Flare angle
          leaves.parent=Parent level
          leaves.size=Cluster size
          leaves.sizePercentOfParent=Size from parent length
          leaves.elongation=Elongation
          leaves.skyInfluence=Sky influence
          leaves.fold=Fold above or below
          leaves.align=Lean along branch
          leaves.reach=Offset from branch
          leaves.strength=Cluster density
          leaves.falloff=Edge falloff
          leaves.noiseScale=Noise size
          leaves.noiseStrength=Noise strength
          leaves.envelopeEdge=Crown edge softness
          scale.x=Width
          scale.y=Length along branch
          scale.z=Depth
          shadePruning.style=Shade style
          shadePruning.threshold=Shade threshold
          """;
  private static final Map<String, String> NAMES = parse();
  private static final Map<String, String> GROUPS = Map.ofEntries(
          Map.entry("generation", "Placement"), Map.entry("length", "Length"), Map.entry("orientation", "Start direction"),
          Map.entry("shape", "Bending"), Map.entry("noise", "Random wander"), Map.entry("jink", "Angular kinks"),
          Map.entry("path", "Wave"), Map.entry("break", "Breakage"), Map.entry("prune", "Pruning"),
          Map.entry("forces", "Force response"), Map.entry("radius", "Thickness"), Map.entry("flares", "Base flare"),
          Map.entry("scale", "Cluster scale"), Map.entry("shadePruning", "Shade pruning"));

  private SettingNames() {
  }

  /**
   * Reads the {@code key=Name} lines of the name table.
   */
  private static Map<String, String> parse() {
    Map<String, String> names = new HashMap<>();
    TABLE.lines().forEach(line -> {
      int equals = line.indexOf('=');
      names.put(line.substring(0, equals), line.substring(equals + 1));
    });
    return names;
  }

  /**
   * The human name for a setting path such as {@code levels[1].radius.min}.
   *
   * @param path Setting path.
   */
  static String of(String path) {
    String written = NAMES.get(lookupKey(path));
    return written != null ? written : spaced(path.substring(path.lastIndexOf('.') + 1));
  }

  /**
   * A fold-out group title with its JSON key, such as {@code Placement (generation)}.
   *
   * @param key JSON key of the group.
   */
  static String group(String key) {
    String name = GROUPS.get(key);
    return name == null ? key : name + " (" + key + ")";
  }

  static boolean hasWrittenName(String path) {
    return NAMES.containsKey(lookupKey(path));
  }

  /**
   * The table key for a setting path: its last two parts without list indexes, so {@code levels[2].radius.min} becomes
   * {@code radius.min}.
   *
   * @param path Setting path.
   */
  static String lookupKey(String path) {
    String[] parts = path.split("\\.");
    if (parts.length < 2) {
      return path;
    }
    String parent = parts[parts.length - 2];
    int bracket = parent.indexOf('[');
    return (bracket < 0 ? parent : parent.substring(0, bracket)) + "." + parts[parts.length - 1];
  }

  /**
   * Turns a key the table cannot know, such as a force's name, into words: {@code sunLight} to Sun light.
   *
   * @param key JSON key.
   */
  private static String spaced(String key) {
    String words = key.replaceAll("([a-z0-9])([A-Z])", "$1 $2").replace('_', ' ').toLowerCase(Locale.ROOT);
    return words.isEmpty() ? key : Character.toUpperCase(words.charAt(0)) + words.substring(1);
  }
}

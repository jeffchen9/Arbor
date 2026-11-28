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
          tree.sizeScale=Size scale|× multiplier|Scales every distance in the design at once, so the whole tree grows or shrinks evenly. 1 keeps it as written; 2 doubles it.
          tree.sizeVariance=Size variance per tree|fraction|Largest random change to each tree's overall size. 0.15 makes each seed between 85% and 115% of the written size; 0 makes every seed the same size.
          tree.maxHeight=Maximum height|blocks|Height where growth is cut off. Branches that reach above it are trimmed, so it is a ceiling, not a target height.
          tree.roll=Tree rotation|degrees|Turns the whole tree around its vertical axis. Give it a tree variance of 180 so each seed faces a different way.
          tree.trunkSpread=Trunk spread|blocks|Typical distance of each trunk from the center when the tree has several trunks. Has no effect on a single trunk.
          tree.groundNormal=Ground slope normal|x, y, z direction|The direction the ground faces, for a tree on a slope. [0, 1, 0] is flat ground; y must be positive. Length does not matter.
          tree.slopeRecovery=Trunk slope recovery|fraction of trunk length|On sloped ground the trunk starts square to the slope; this is how much of the trunk it takes to turn back to upright. 0 grows straight up at once.
          shapeControl.style=Crown shape style|choice|Whether a crown envelope limits the branches. none lets branches grow freely; envelope keeps them inside the crown radius curve below.
          shapeControl.crownBase=Crown base height|blocks (before size scale)|Height where the crown envelope starts. Below it the envelope does not limit branches.
          shapeControl.relativeRadius=Radius as share of tree height|on/off|When on, the crown radius curve is a share of the tree's height (0.3 is 30%) instead of blocks.
          shapeControl.radius=Crown radius by height|curve: [height 0 to 1, radius]|The crown's radius at each height, from the crown base (0) to the tree top (1). Radius is in blocks before size scale, or a share of tree height when relative.
          shapeControl.action=At the crown edge|choice|What happens to a branch that reaches the crown edge. stop cuts it at the edge; scaleLength shortens it before it grows so it just fits.
          shapeControl.steer=Steer along crown edge|0 to 1|How strongly branches near the crown edge turn to run along it instead of poking out. 0 does not steer.
          shapeControl.lumpiness=Crown lumpiness|share of radius, 0 to 1|How far the crown's edge bulges out and dents in, so the crown reads as several rounded masses instead of one smooth dome. Each tree gets its own lumps. 0 is smooth; 0.2 to 0.35 suits an oak.
          shapeControl.lumpSize=Lump size|blocks (before size scale)|Typical width of one bulge of the crown. Large values give a few big masses; small values give many small bumps.
          simple.size=Size|× multiplier|Scales the whole tree evenly without changing its shape. 1 keeps the species' size; 2 doubles it.
          simple.height=Trunk height|× multiplier|Makes the trunk shorter or taller while the crown keeps its width. 1 keeps the species' height; 1.5 is half again as tall.
          simple.crownStart=Crown starts at|share of trunk, 0 to 0.95|How far up the trunk the lowest branches grow, so the trunk below is bare. 0.5 starts the crown halfway up; 0.95 leaves 95% bare trunk.
          simple.crownWidth=Crown width|× multiplier|Makes the crown narrower or wider by scaling branch lengths and the crown radius together. 1 keeps the species' width.
          simple.trunkThickness=Trunk thickness|× multiplier|Makes the trunk thinner or thicker. 1 keeps the species' thickness.
          simple.branchDensity=Branch density|× multiplier|Changes the total number of final branches. 1 keeps the species' count; 2 roughly doubles it.
          simple.branchAngle=Branch angle|degrees|Added to every branch's start angle. Positive spreads branches out toward horizontal; negative points them up.
          simple.droop=Droop|-1 to 1 (extremes to ±2)|Vertical pull on all branches. Positive lets branches hang down; negative lifts them. 0 keeps the species as written.
          simple.twist=Twist|× multiplier|Scales how much branches wander. 0 makes them straight; 1 keeps the species; higher is more crooked.
          simple.logBranches=Log branches|× multiplier|How many branches are drawn as full logs instead of walls and fences. 1 keeps the species; 2 turns branches half as thick into logs, for a chunkier tree; below 1 draws more fences.
          simple.leafFullness=Leaf fullness|× multiplier|Scales leaf cluster size and density together. Below 1 is sparser foliage; above 1 is fuller.
          simple.windLean=Wind lean|0 to 1 (up to 2)|A steady sideways lean, as if the tree grew in constant wind. 0 is no lean.
          simple.sizeVariety=Size variety|fraction|Replaces the species' size variance per tree: how much tree size differs between seeds. 0.2 gives 80% to 120%.
          forces.name=Force name|text|The force's name. Each level's force response refers to the force by this name.
          forces.type=Force type|choice|How the force pushes branches. direction pushes one way everywhere; attractor pulls toward a point (like the sun); curl spins branches around an axis; wind pushes sideways with gusts and grows with height.
          forces.vector=Direction|x, y, z direction|The way a direction or wind force pushes, or the axis a curl force spins around. [1, 0, 0] points along x. Length does not matter.
          forces.position=Attractor position|x, y, z in blocks|The point an attractor force pulls toward, measured from the trunk base. [0, 90, 0] is high above the tree, like the sun.
          forces.strength=Strength|bend per block grown|How much the force bends branches for each block they grow. Small values such as 0.02 to 0.05 are typical; negative values push the other way.
          forces.falloff=Distance falloff|per block|How fast the force weakens with distance: strength is divided by 1 + falloff × distance. 0 is equally strong everywhere.
          forces.directionVariance=Wind direction variance|degrees|Largest random turn of the wind's heading, chosen once per tree, so seeds lean different ways.
          forces.gust=Gust strength|0 to 1|How much gusts raise and lower the wind through the tree, giving uneven, wind-tossed branches. 0 is steady wind.
          forces.gustSize=Gust size|blocks|Typical size of one gust. Small gusts vary branch to branch; large ones sweep whole sides of the tree.
          forces.heightPower=Wind increase with height|power|How fast the wind grows from the ground to the tree top. 0 is equally strong at every height; 1 grows evenly with height; 2 is strongest near the top.
          forces.flexibility=Thin branch flexibility|× extra bend|Extra wind bend on thin branches. Branches 1 block in radius or thicker get none; thinner ones bend more.
          shapes.branchDensity=Survival branch density|× multiplier|Survival mode only. Without fences every branch becomes a log, so fewer branches keep the tree from turning into a block of wood. 0.5 grows half as many final branches as Advanced; 1 grows the same number.
          shapes.fullRadius=Logs from thickness|blocks of radius|Picks blocks by thickness, it does not make branches thicker: branches at least this thick are drawn as logs, thinner ones as walls or fences. Lower values draw more of the tree as logs, so it looks chunkier.
          shapes.wallRadius=Walls from thickness|blocks of radius|Branches at least this thick, but thinner than Logs from thickness, are drawn as walls; thinner ones as fences. Lower values draw fewer fences.
          shapes.wallGapChance=Wall gap chance|chance 0 to 1|Chance of skipping a wall block along a side branch, which makes thin limbs look less solid.
          shapes.thinCoverage=Thin branch coverage|chance 0 to 1|Chance that each fence position along a thin branch is placed. Lower values leave gaps in the finest branches.
          shapes.gateChance=Fence gate chance|chance 0 to 1|Chance that a straight run of fence becomes a closed fence gate, for a flatter twig shape.
          shapes.gateInWallChance=Lowered gate chance|chance 0 to 1|Chance that a fence gate uses its lowered (in-wall) look.
          shapes.openGateChance=Open gate at splits|chance 0 to 1|Chance that a point where a thin branch splits becomes an open fence gate, which reads as a fork.
          shapes.impliedRadius=Implied branch radius|blocks|Branch ends thinner than this are drawn as leaf clusters instead of wood, so the finest twigs vanish into foliage. 0 keeps all wood.
          shapes.impliedLeafSize=Implied leaf size|blocks|Radius of the leaf clusters that replace those too-thin branch ends.
          shapes.impliedLeafStrength=Implied leaf strength|0 to 1|Density of the leaf clusters that replace too-thin branch ends. 1 is solid.
          shapes.deadBushChance=Dead bush chance|chance 0 to 1|Chance that a crowded fence gets a dead bush or sapling on it, for a twiggy look.
          shapes.deadBushDensity=Dead bush crowding|count|How many nearby fences and gates a fence needs to count as crowded and be able to get a dead bush.
          shapes.deadBushOffFenceChance=Dead bush replaces fence|chance 0 to 1|Chance that the dead bush or sapling replaces the fence instead of sitting on top of it.
          shapes.smoothTrunk=Smooth trunk|on/off|Softens the trunk's surface with stairs, slabs, walls and fences so thick trunks look rounder.
          shapes.smoothStairDepth=Stair smoothing depth|blocks of depth|Surface blocks at most this deep inside the trunk can become stairs when smoothing.
          shapes.smoothSlabDepth=Slab smoothing depth|blocks of depth|Surface blocks at most this deep inside the trunk can become slabs when smoothing.
          shapes.smoothWallDepth=Wall smoothing depth|blocks of depth|Surface blocks at most this deep inside the trunk can become walls when smoothing.
          shapes.smoothFenceDepth=Fence smoothing depth|blocks of depth|Surface blocks at most this deep inside the trunk can become fences when smoothing.
          shapes.buttonChance=Trunk button chance|chance 0 to 1|Chance of a button on each suitable trunk face, as bark texture or knots.
          shapes.buttonSpacing=Trunk button spacing|blocks|Smallest distance between two trunk buttons.
          shapes.tipCanopyDensity=Bare tip trim density|leaf density 0 to 1|A thin branch tip needs at least this much leaf density around it to stay; bare tips sticking out of the crown are trimmed. 0 keeps bare tips.
          shapes.fineBranchChance=Fine branch hint chance|chance 0 to 1|Chance that a branch too fine to draw is hinted with a short fence arm or gate instead of disappearing.
          shapes.fineGateChance=Fine branch gate chance|chance 0 to 1|Chance that such a hint is an open fence gate instead of a fence arm.
          shapes.trunkFullRadius=Thin trunk: no logs below|blocks of radius|A trunk thinner than this is drawn only with walls and fences, for saplings and very thin trees. 0 turns this off.
          shapes.saplingLeafDensity=Sapling leaf density|leaf density 0 to 1|Where a crowded fence has at least this much leaf density, it gets a sapling instead of a dead bush.
          shapes.voxelRandomness=Voxel randomness|0 to 1|Overall irregularity when turning branches into blocks: jitters thickness thresholds and branch tips. 0 is perfectly regular.
          shapes.leafBlockDensity=Full leaf block density|leaf density 0 to 1|Leaf density needed for a leaf block. Lower values give fuller, blockier foliage; higher values give sparser foliage. 0 places no leaf blocks.
          shapes.trimToCanopy=Trim wood to canopy|on/off|Stops walls and fences at the last point that has leaves at or above it, so bare branch ends do not stick out of the crown.
          shapes.mode=Voxelizer mode|choice|advanced uses every block shape, plants and fine detail; survival uses only logs, six-sided wood and leaves, with leaves kept within 6 blocks of a log so they do not decay.
          blocks.log=Logs|weighted blocks and mixing rule|Blocks for full-thickness wood, with weights for how often each is used, and how they mix: at random block by block, in clumps (noise rule), in height bands (height rule) or by branch level (level rule).
          blocks.junction=Six-sided wood|weighted blocks and mixing rule|Blocks used where wood turns or branches meet, so bark shows on every side.
          blocks.cutEnd=Cut ends|weighted blocks and mixing rule|Blocks for the cut faces of logs, such as where a broken branch ends.
          blocks.planks=Planks|weighted blocks and mixing rule|The wood type whose stairs and slabs smooth the trunk: oak_planks gives oak stairs and oak slabs.
          blocks.stairs=Stairs|weighted blocks and mixing rule|Stair blocks for smoothing the trunk surface.
          blocks.slab=Slabs|weighted blocks and mixing rule|Slab blocks for smoothing the trunk surface.
          blocks.wall=Walls|weighted blocks and mixing rule|Wall blocks for medium-thin branches.
          blocks.fence=Fences|weighted blocks and mixing rule|Fence blocks for the thinnest branches.
          blocks.gate=Fence gates|weighted blocks and mixing rule|Fence gate blocks for twig hints and branch splits.
          blocks.button=Buttons|weighted blocks and mixing rule|Button blocks for trunk texture.
          blocks.sapling=Saplings|weighted blocks and mixing rule|Sapling blocks placed on crowded fences inside the foliage.
          blocks.deadBush=Dead bushes|weighted blocks and mixing rule|Dead bush blocks placed on crowded fences outside the foliage.
          blocks.leaves=Leaves|weighted blocks and mixing rule|Leaf blocks, with weights for how often each is used and a rule for how they mix.
          plantLayers.name=Layer name|text|The layer's unique name. It also fixes this layer's random placement, so renaming it moves its plants.
          plantLayers.enabled=Enabled|on/off|Whether this plant layer is used at all.
          plantLayers.treeChance=Chance per tree|chance 0 to 1|Chance that a given seed uses this layer, so only some trees get, say, berries.
          plantLayers.blocks=Plants|weighted blocks|The plants this layer places, with weights for how often each is used.
          plantLayers.chance=Chance per block|chance 0 to 1|Chance of planting at each spot that meets this layer's other limits.
          plantLayers.leafDensity=Leaf density range|range [min, max], 0 to 1|Plants only go where the leaf density is between these two values: low for the crown's edge, high for its dense inside.
          plantLayers.height=Height range|range [min, max], 0 to 1 of tree height|Plants only go between these heights, from 0 (lowest tree block) to 1 (highest).
          plantLayers.side=Crown side|choice|Which part of the crown plants may go on: any, top (on top of foliage), underside, outer, inner, or onTallPlant (on top of another tall plant).
          plantLayers.nearBranch=Near branch level|level name|Plants must be near branches of this level; any allows any leaves or branch.
          plantLayers.nearDistance=Distance from branch|blocks|How far from that branch level plants may be.
          plantLayers.clumpSize=Clump size|blocks|Size of the noise that groups plants into clumps. 0 scatters them evenly.
          plantLayers.doubleHalf=Double plant halves|choice|For two-block plants: both places the whole plant; bottom or top places only that half.
          pinecones.enabled=Enabled|on/off|Whether pinecones are added.
          pinecones.chance=Chance per spot|chance 0 to 1|Chance for each open spot beside a leaf block to get a pinecone.
          pinecones.undersideShare=Underside share|0 to 1|Share of pinecones hanging under a leaf block rather than on its side. 1 is underneath only.
          pinecones.height=Height range|range [min, max], 0 to 1 of tree height|Pinecones only go between these heights.
          pinecones.minLevel=Lowest branch level|level number|The lowest branch level that holds pinecones. 0 is the trunk, so 2 skips the trunk and main branches.
          pinecones.blocks=Pinecone blocks|weighted blocks|Blocks used as pinecones, usually buttons, with weights.
          vines.enabled=Enabled|on/off|Whether vines are added.
          vines.chance=Chance per spot|chance 0 to 1|Chance for each open spot below leaves or logs to start a hanging strand.
          vines.minLength=Shortest strand|blocks|Length of the shortest vine strand.
          vines.maxLength=Longest strand|blocks|Length of the longest vine strand.
          vines.blocks=Vine blocks|weighted blocks|Blocks used for the strands, such as vines or weeping vines, with weights.
          moss.enabled=Enabled|on/off|Whether moss is added.
          moss.amount=Amount|0 to 1|How much of the exposed log surface moss covers.
          moss.patchSize=Patch size|blocks|Rough width of each moss patch.
          moss.upward=Prefer upward faces|0 to 1|How strongly moss keeps to logs with an open top, as moss grows where rain lands. 0 ignores direction.
          moss.height=Height range|range [min, max], 0 to 1 of tree height|Moss only goes between these heights.
          moss.blocks=Moss blocks|weighted blocks|Blocks that replace the logs, such as moss blocks, with weights.
          levels.name=Level name|text|This level's unique name. Child levels, leaves and plant layers refer to it by name.
          levels.parent=Parent level|level name|The level this one grows from. Left out, it grows from the level just before it.
          levels.segmentLength=Segment length|blocks|Length of each straight step a branch grows in. Smaller steps make smoother curves but cost more work.
          levels.wood=Draw as wood|on/off|Whether this level becomes wood. When off, its branches are invisible and only carry leaves or guide plant layers.
          generation.mode=Placement mode|choice|How children are placed on each parent: flood (count at random positions), absolute (count spaced evenly), proportional (density per block of parent), interval (groups at a set spacing), phyllotaxy (plant-like node patterns), bifurcation (at the parent's bends), or parent (the trunk only: at the origin).
          generation.first=First position|0 to 1 along parent|Earliest point on the parent where children grow, from 0 (base) to 1 (tip). 0.3 leaves the bottom 30% bare.
          generation.last=Last position|0 to 1 along parent|Latest point on the parent where children grow, from 0 (base) to 1 (tip).
          generation.clip=Clip to range|on/off|Whether children pushed outside the first-to-last range by offsets are removed instead of kept.
          generation.rotation=Rotation|degrees|Added to each child's roll around its parent.
          generation.position=Position offset|fraction of parent length|Shifts each child along the parent; with variance it scatters children.
          generation.sink=Sink into parent|blocks|How far each child starts inside its parent, behind its attachment point, so joints look grown in.
          generation.pruning=Pruning curve|curve: [position 0 to 1, value]|Over the parent's length from base (0) to tip (1): children are removed wherever the curve is below 0.5.
          generation.knockout=Random removal|chance 0 to 1|Chance of removing each child, for an irregular, gappy look.
          generation.extendParent=Extra tip children|count|Extra children placed at the parent's tip that continue in its direction, so the parent seems to keep growing.
          generation.count=Count|count|Children in total for flood and absolute modes, or per group in interval mode. For the trunk level, the number of trunks.
          generation.density=Count per block|per block of parent length|Children for each block of parent length, in proportional mode. Longer parents get more children.
          generation.frequency=Group spacing|blocks|Distance between groups along the parent, in interval mode.
          generation.phyllotaxy=Phyllotaxy pattern|choice|Node pattern in phyllotaxy mode: opposite_distichous (pairs facing one way), opposite_decussate (pairs turning 90° each node), opposite_random, alternate_distichous (single children on alternate sides), alternate_random, or whorled (rings of several).
          generation.childrenPerNode=Children per node|count|How many children each ring has in the whorled pattern.
          generation.internodeLength=Internode length|blocks|Distance between nodes in phyllotaxy mode.
          generation.spread=Position spread|fraction of group spacing|Random shift of each child along the parent, so groups are less regular.
          generation.spiral=Spiral angle|degrees|Roll added for each child or group, so they spiral around the parent. 137.5 gives the even spread seen in many plants.
          generation.limit=Group limit|count|Most groups or bends used per parent. 0 means no limit.
          generation.align=Align to sky|0 to 1|How far children turn toward the sky around the parent, so they grow from the parent's upper side. 0 keeps them all around.
          generation.balance=Group balance|0 to 1|How far each group rotates to fill the gaps left by the previous group.
          generation.threshold=Bend threshold|degrees|In bifurcation mode, the smallest bend in the parent that can grow a child.
          generation.spacing=Bend spacing|blocks|In bifurcation mode, the smallest distance between chosen bends.
          generation.locality=Bend preference|0 to 1|In bifurcation mode, which bends are chosen first: 0 sharpest first, 1 gentlest first.
          length.absolute=Length in blocks|blocks|The branch's base length. It is added to the parent- and radius-based lengths.
          length.percentOfParent=Fraction of parent length|fraction of parent length|Length added as a share of the parent's length. 0.5 makes a branch half as long as its parent.
          length.percentOfRadius=Multiple of radius|× branch base radius|Length added as a multiple of the branch's own base radius, so thicker branches grow longer.
          length.min=Minimum length|blocks|The shortest a branch may be.
          length.max=Maximum length|blocks|The longest a branch may be.
          length.shapeScale=Crown fit scale|× multiplier|With the crown action scaleLength, scales the length that fits inside the crown. Above 1 lets branches poke past the edge.
          length.stopShapeControl=Ignore crown shape|on/off|When on, this level and its children ignore the crown envelope.
          length.crownReach=Crown reach|share of crown radius, 0.05 to 2|How far toward the crown's edge this level may grow: 1 reaches the edge, 0.75 stops a quarter of the way short. Lower it on big limbs so the outer twigs and leaves form the edge, and branch ends stop at different distances instead of on one surface.
          orientation.startAngle=Start angle|fraction of 180°|Angle between the branch and its parent's direction where it starts: 0 continues straight, 0.5 is square to the parent (90°), 1 points back.
          orientation.startRoll=Start roll|degrees|Added to the branch's roll around its parent.
          orientation.alignment=Align frame to sky|0 to 1|How far the roll's zero direction turns toward the sky, so roll angles are measured from up instead of a fixed side.
          shape.gravity=Gravity|bend per block grown|Bends the branch downward as it grows. Small values such as 0.02 droop gently; negative values bend it upward.
          shape.ancestor=Pull toward parent|bend per block grown|Bends the branch toward its parent's direction as it grows; negative values bend it away.
          shape.straightness=Straightness|0 to 1|Reduces random wander near the branch's base, so it leaves its parent cleanly. 0 wanders everywhere.
          shape.twist=Twist|degrees|How far the branch's heading rotates around its starting direction over its length, for a corkscrew look.
          shape.curlUp=Curl along parent|degrees|Total bend toward the parent's direction over the branch's length.
          shape.curlRight=Curl sideways|degrees|Total sideways bend around the parent over the branch's length.
          shape.curlOut=Curl outward|degrees|Total bend away from the parent over the branch's length.
          shape.zigzag=Zigzag angle|degrees|Angle of each side-to-side zigzag turn. 0 is no zigzag.
          shape.zigzagLength=Zigzag length|blocks|Length of each zigzag leg.
          noise.earlyAmount=Growth noise|turn per block grown|Random turning while the branch grows. It builds up into broad curves, so the branch ends up somewhere different.
          noise.earlyTurbulence=Growth noise detail|0 to 1|How often the growth noise changes along the branch: low gives long smooth curves, high gives many small turns.
          noise.earlyThreshold=Growth noise threshold|0 to 1|Growth noise smaller than this is ignored, so only bigger turns remain.
          noise.lateAmount=Offset noise|blocks|Largest sideways wobble added after the branch has grown. It roughens the branch without changing where it goes.
          noise.lateTurbulence=Offset noise detail|0 to 1|How often the wobble changes along the branch: low is gentle waves, high is jagged.
          noise.lateSmooth=Offset noise smoothing|0 to 1|Smooths the wobble by using fewer changes.
          jink.amount=Jink amount|0 to 1|How far the branch is straightened into angular pieces with sharp kinks between them. 0 keeps it smooth.
          jink.segments=Jink segments|count|How many straight pieces the branch is split into.
          jink.uniformity=Jink uniformity|0 to 1|How evenly the pieces are spaced: 0 irregular, 1 even.
          path.amount=Wave size|blocks|Height of a regular wave along the branch. 0 is no wave.
          path.twist=Wave twist|degrees per wave|How far the wave's side direction turns each wave, so it spirals instead of staying flat.
          path.length=Wave length|blocks|Distance of one full wave along the branch.
          path.offset=Wave phase|fraction of a wave|Shifts where the wave starts along the branch.
          break.chance=Break chance|chance 0 to 1|Chance that a branch of this level breaks off part way, for an old or storm-damaged look.
          break.spot=Break position|fraction of length kept|How much of the branch is kept when it breaks, from 0.05 to 1.
          prune.interior=Prune inward branches|chance 0 to 1|Chance of removing branches that point back toward the trunk, more for those that point more directly.
          prune.up=Prune upward branches|chance 0 to 1|Chance of removing branches that point up, more for steeper ones.
          prune.down=Prune downward branches|chance 0 to 1|Chance of removing branches that point down, more for steeper ones.
          prune.general=Prune at random|chance 0 to 1|Chance of removing any branch of this level.
          prune.ground=Prune below ground|chance 0 to 1|Chance of removing branches that dip below ground level.
          radius.absolute=Radius in blocks|blocks|The branch's base radius (half its thickness). It is added to the parent-based radius.
          radius.percentOfParent=Fraction of parent radius|fraction of parent radius|Radius added as a share of the parent's radius where the branch attaches.
          radius.taper=Taper curve|curve: [position 0 to 1, × radius]|How the radius changes along the branch from base (0) to tip (1), as a multiplier: [[0, 1], [1, 0.2]] narrows to 20% at the tip.
          radius.clampToParent=Limit to parent radius|on/off|Keeps a branch from starting thicker than its parent at the attachment point.
          radius.min=Minimum radius|blocks|The thinnest a branch may start.
          radius.max=Maximum radius|blocks|The thickest a branch may start.
          radius.lengthCompensation=Taper compensation|0 to 1|Reduces the taper toward the tip, so long branches stay thicker further out.
          radius.lengthKillRatio=Maximum length to radius|ratio|Removes branches whose length divided by base radius is above this, so overly long, thin branches vanish. 0 turns this off.
          flares.radius=Flare radius|× branch radius|Extra radius at the very base, like the buttress of a big trunk. 1 doubles the radius there.
          flares.height=Flare height|blocks|How far up the branch the flare fades out.
          flares.pinch=Flare pinch|power|How fast the flare fades: higher values keep it tight to the base.
          flares.lobes=Flare lobes|count|Number of root-like bulges around the base. 0 flares evenly all round.
          flares.noise=Flare noise|0 to 1|Irregularity of the flare around the branch.
          flares.angle=Flare angle|degrees|Turns the lobes around the branch.
          leaves.parent=Parent level|level name|The branch level whose branches carry these leaf clusters.
          leaves.size=Cluster size|blocks|Radius of each leaf cluster.
          leaves.sizePercentOfParent=Size from parent length|fraction of parent length|Extra cluster radius as a share of the carrying branch's length, so longer branches carry bigger clusters.
          leaves.elongation=Elongation|× multiplier|Stretches clusters along their stretch axis only. Above 1 makes them longer.
          leaves.skyInfluence=Sky influence|0 to 1|How far each cluster's stretch and offset turn toward the sky instead of following the branch.
          leaves.fold=Fold above or below|-1 to 1|Moves clusters above (positive) or below (negative) the branch.
          leaves.align=Lean along branch|-1 to 1|Moves clusters toward the branch tip (positive) or base (negative).
          leaves.reach=Offset from branch|0 to 1 of cluster size|How far clusters sit off the branch, as a share of their size.
          leaves.strength=Cluster density|0 to 1|Leaf density at each cluster's center. Higher gives more solid foliage.
          leaves.falloff=Edge falloff|power|How fast density fades from center to edge. Higher gives softer, smaller-looking clusters.
          leaves.noiseScale=Noise size|blocks|Size of the noise pattern that breaks up foliage density. Small is fine-grained; large makes big gaps.
          leaves.noiseStrength=Noise strength|0 to 1|How much the noise can remove. 0 gives smooth foliage; 1 gives very patchy foliage.
          leaves.envelopeEdge=Crown edge softness|blocks|Distance over which foliage fades out at the crown envelope's edge.
          scale.x=Width|× multiplier|Cluster radius multiplier across the cluster, on its side-to-side axis.
          scale.y=Length along branch|× multiplier|Cluster radius multiplier along the cluster's stretch axis.
          scale.z=Depth|× multiplier|Cluster radius multiplier across the cluster, on its front-to-back axis.
          shadePruning.style=Shade style|choice|Removes part of each cluster: none, interior (hollow clusters, like shaded inner leaves dying back), or exterior (only the inner part remains).
          shadePruning.threshold=Shade threshold|0 to 1 from center|Where removal starts, as a distance from the cluster's center (0) to its edge (1).
          """;
  private static final Map<String, String[]> TABLE_ROWS = parse();
  private static final Map<String, String> NAMES = column(0);
  private static final Map<String, String> UNITS = column(1);
  private static final Map<String, String> DESCRIPTIONS = column(2);
  private static final Map<String, String> GROUPS = Map.ofEntries(
          Map.entry("generation", "Placement"), Map.entry("length", "Length"), Map.entry("orientation", "Start direction"),
          Map.entry("shape", "Bending"), Map.entry("noise", "Random wander"), Map.entry("jink", "Angular kinks"),
          Map.entry("path", "Wave"), Map.entry("break", "Breakage"), Map.entry("prune", "Pruning"),
          Map.entry("forces", "Force response"), Map.entry("radius", "Thickness"), Map.entry("flares", "Base flare"),
          Map.entry("scale", "Cluster scale"), Map.entry("shadePruning", "Shade pruning"));

  private SettingNames() {
  }

  /**
   * Reads the {@code key=Name|unit|description} lines of the table.
   */
  private static Map<String, String[]> parse() {
    Map<String, String[]> rows = new HashMap<>();
    TABLE.lines().forEach(line -> {
      int equals = line.indexOf('=');
      rows.put(line.substring(0, equals), line.substring(equals + 1).split("\\|", 3));
    });
    return rows;
  }

  private static Map<String, String> column(int index) {
    Map<String, String> column = new HashMap<>();
    TABLE_ROWS.forEach((key, row) -> column.put(key, row[index]));
    return column;
  }

  /**
   * What a setting's number or text is measured in, such as blocks or degrees.
   *
   * @param path Setting path.
   */
  static String unit(String path) {
    String unit = UNITS.get(lookupKey(path));
    return unit != null ? unit : isForceResponse(path) ? "× multiplier" : "";
  }

  /**
   * What a setting does, in plain words.
   *
   * @param path Setting path.
   */
  static String description(String path) {
    String description = DESCRIPTIONS.get(lookupKey(path));
    if (description != null) {
      return description;
    }
    return isForceResponse(path) ? "How strongly this level responds to the force named "
            + path.substring(path.lastIndexOf('.') + 1) + ": 1 is fully, 0 ignores it, negative pushes the other way."
            : "";
  }

  /**
   * A level's response to one named force, such as {@code levels[2].forces.wind}.
   *
   * @param path Setting path.
   */
  private static boolean isForceResponse(String path) {
    return path.matches("levels\\[\\d+]\\.forces\\.[^.]+");
  }

  /**
   * A tooltip: the description, then the unit.
   *
   * @param path Setting path.
   */
  static String tip(String path) {
    String description = description(path);
    String unit = unit(path);
    return description.isEmpty() ? null : description + (unit.isEmpty() ? "" : " (" + unit + ")");
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

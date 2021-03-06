/*
 * Copyright (C) 2013-2020 Byron 3D Games Studio (www.b3dgs.com) Pierre-Alexandre (contact@b3dgs.com)
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package com.b3dgs.warcraft.object;

import com.b3dgs.lionengine.Origin;
import com.b3dgs.lionengine.game.FeatureProvider;
import com.b3dgs.lionengine.game.FramesConfig;
import com.b3dgs.lionengine.game.Tiled;
import com.b3dgs.lionengine.game.feature.Actionable;
import com.b3dgs.lionengine.game.feature.FeatureGet;
import com.b3dgs.lionengine.game.feature.FeatureInterface;
import com.b3dgs.lionengine.game.feature.FeatureModel;
import com.b3dgs.lionengine.game.feature.Recyclable;
import com.b3dgs.lionengine.game.feature.Services;
import com.b3dgs.lionengine.game.feature.Setup;
import com.b3dgs.lionengine.game.feature.Transformable;
import com.b3dgs.lionengine.game.feature.attackable.Attacker;
import com.b3dgs.lionengine.game.feature.attackable.AttackerListener;
import com.b3dgs.lionengine.game.feature.attackable.AttackerListenerVoid;
import com.b3dgs.lionengine.game.feature.collidable.Collidable;
import com.b3dgs.lionengine.game.feature.collidable.selector.Hud;
import com.b3dgs.lionengine.game.feature.collidable.selector.Selectable;
import com.b3dgs.lionengine.game.feature.collidable.selector.Selector;
import com.b3dgs.lionengine.game.feature.producible.Producer;
import com.b3dgs.lionengine.game.feature.producible.Producible;
import com.b3dgs.lionengine.game.feature.producible.ProducibleListener;
import com.b3dgs.lionengine.game.feature.producible.ProducibleListenerVoid;
import com.b3dgs.lionengine.game.feature.state.StateHandler;
import com.b3dgs.lionengine.game.feature.tile.Tile;
import com.b3dgs.lionengine.game.feature.tile.map.MapTile;
import com.b3dgs.lionengine.game.feature.tile.map.extractable.Extractor;
import com.b3dgs.lionengine.game.feature.tile.map.extractable.ExtractorListener;
import com.b3dgs.lionengine.game.feature.tile.map.extractable.ExtractorListenerVoid;
import com.b3dgs.lionengine.game.feature.tile.map.pathfinding.MapTilePath;
import com.b3dgs.lionengine.game.feature.tile.map.pathfinding.Pathfindable;
import com.b3dgs.lionengine.game.feature.tile.map.transition.MapTileTransition;
import com.b3dgs.lionengine.graphic.drawable.Drawable;
import com.b3dgs.lionengine.graphic.drawable.SpriteAnimated;
import com.b3dgs.warcraft.Player;
import com.b3dgs.warcraft.Util;
import com.b3dgs.warcraft.constant.Constant;
import com.b3dgs.warcraft.object.feature.EntityStats;
import com.b3dgs.warcraft.object.state.StateIdle;

/**
 * Entity model implementation.
 */
@FeatureInterface
public final class EntityModel extends FeatureModel implements Recyclable
{
    private final AttackerListener attackerListener = new AttackerListenerVoid()
    {
        @Override
        public void notifyAttackStarted(Transformable target)
        {
            attackStarted = true;
        }

        @Override
        public void notifyAttackStopped()
        {
            attackStarted = false;
        }
    };
    private final ProducibleListener producibleListener = new ProducibleListenerVoid()
    {
        @Override
        public void notifyProductionEnded(Producer producer)
        {
            producibleEnded = true;
        }
    };
    private final ExtractorListener extractorListener = new ExtractorListenerVoid()
    {
        @Override
        public void notifyStartGoToRessources(String type, Tiled resourceLocation)
        {
            if (carryResource == null)
            {
                pathfindable.setDestination(resourceLocation);
                gotoResource = true;
            }
        }

        @Override
        public void notifyStartExtraction(String type, Tiled resourceLocation)
        {
            extractResource = type;
            if (Constant.RESOURCE_WOOD.equals(type))
            {
                pathfindable.pointTo(resourceLocation);
            }
        }

        @Override
        public void notifyStartCarry(String type, int totalQuantity)
        {
            final Tiled warehouse = Util.getWarehouse(services, stats.getRace());
            if (warehouse != null)
            {
                pathfindable.setDestination(warehouse);
                extractResource = null;
                carryResource = type;

                if (Constant.RESOURCE_WOOD.equals(type))
                {
                    cutWood();
                }

                switchActionExtractCarry();
            }
            else
            {
                extractor.stopExtraction();
            }
        }

        @Override
        public void notifyStartDropOff(String type, int totalQuantity)
        {
            setVisible(false);
            if (player.owns(EntityModel.this))
            {
                player.increaseResource(type, totalQuantity);
            }
        }

        @Override
        public void notifyDroppedOff(String type, int droppedQuantity)
        {
            if (droppedQuantity == 0)
            {
                setVisible(true);
                carryResource = null;
            }
        }

        @Override
        public void notifyStopped()
        {
            gotoResource = false;
            extractResource = null;
        }
    };

    private final SpriteAnimated surface;

    private final Player player = services.get(Player.class);
    private final Hud hud = services.get(Hud.class);
    private final Selector selector = services.get(Selector.class);
    private final MapTile map = services.get(MapTile.class);
    private final MapTilePath mapPath = map.getFeature(MapTilePath.class);
    private final MapTileTransition mapTransition = map.getFeature(MapTileTransition.class);

    @FeatureGet private Transformable transformable;
    @FeatureGet private Collidable collidable;
    @FeatureGet private Selectable selectable;
    @FeatureGet private Pathfindable pathfindable;
    @FeatureGet private Extractor extractor;
    @FeatureGet private Attacker attacker;
    @FeatureGet private Producible producible;
    @FeatureGet private StateHandler stateHandler;
    @FeatureGet private EntityStats stats;

    private boolean attackStarted;
    private boolean producibleEnded;
    private boolean gotoResource;
    private String extractResource;
    private String carryResource;

    private boolean visible = true;
    private boolean display = true;

    /**
     * Create model.
     * 
     * @param services The services reference.
     * @param setup The setup reference.
     */
    public EntityModel(Services services, Setup setup)
    {
        super(services, setup);

        final FramesConfig config = FramesConfig.imports(setup);
        surface = Drawable.loadSpriteAnimated(setup.getSurface(), config.getHorizontal(), config.getVertical());
        surface.setOrigin(Origin.BOTTOM_LEFT);
        surface.setFrameOffsets(config.getOffsetX(), config.getOffsetY());
    }

    @Override
    public void prepare(FeatureProvider provider)
    {
        super.prepare(provider);

        attacker.addListener(attackerListener);
        producible.addListener(producibleListener);
        extractor.addListener(extractorListener);
    }

    /**
     * Set the visible flag.
     * 
     * @param visible <code>true</code> if visible, <code>false</code> else.
     */
    public void setVisible(boolean visible)
    {
        this.visible = visible;
        collidable.setEnabled(visible);
        if (!visible && selector.getSelection().remove(selectable))
        {
            selectable.onSelection(false);
            hud.clearMenus();
        }
    }

    /**
     * Set display flag.
     * 
     * @param display The display flag.
     */
    public void setDisplay(boolean display)
    {
        this.display = display;
    }

    /**
     * Check visible flag.
     * 
     * @return <code>true</code> if visible, <code>false</code> else.
     */
    public boolean isVisible()
    {
        return visible;
    }

    /**
     * Check display flag.
     * 
     * @return The display flag.
     */
    public boolean isDisplay()
    {
        return display;
    }

    /**
     * Get the surface reference.
     * 
     * @return The surface reference.
     */
    public SpriteAnimated getSurface()
    {
        return surface;
    }

    /**
     * Get the services reference.
     * 
     * @return The services reference.
     */
    public Services getServices()
    {
        return services;
    }

    /**
     * Check if move started.
     * 
     * @return <code>true</code> if move started, <code>false</code> else.
     */
    public boolean isMoveStarted()
    {
        return pathfindable.isMoving();
    }

    /**
     * Check if move arrived.
     * 
     * @return <code>true</code> if move arrived, <code>false</code> else.
     */
    public boolean isMoveArrived()
    {
        return !pathfindable.isMoving();
    }

    /**
     * Check if attack started.
     * 
     * @return <code>true</code> if attack started, <code>false</code> else.
     */
    public boolean isAttackStarted()
    {
        return attackStarted;
    }

    /**
     * Check if production ended.
     * 
     * @return <code>true</code> if production ended, <code>false</code> else.
     */
    public boolean isProduced()
    {
        return producibleEnded;
    }

    /**
     * Check if going to resource.
     * 
     * @return <code>true</code> if going to resource, <code>false</code> else.
     */
    public boolean isGotoResource()
    {
        return gotoResource;
    }

    /**
     * Get the extracting resource type.
     * 
     * @return The extracting resource type, <code>null</code> if none.
     */
    public String getExtractResource()
    {
        return extractResource;
    }

    /**
     * Get the carrying resource type.
     * 
     * @return The carrying resource type, <code>null</code> if none.
     */
    public String getCarryResource()
    {
        return carryResource;
    }

    /**
     * Reset states flag.
     */
    public void resetFlags()
    {
        attackStarted = false;
        producibleEnded = false;
        extractResource = null;
    }

    /**
     * Cut wood tile and search next tree.
     */
    private void cutWood()
    {
        final Tile tile = mapPath.getTile(extractor.getResourceLocation());
        map.setTile(tile.getInTileX(), tile.getInTileY(), Constant.TILE_NUM_TREE_CUT);
        mapTransition.resolve(map.getTile(tile.getInTileX(), tile.getInTileY()));

        final Tile next = Util.getClosestTree(map, mapPath, tile, transformable);
        if (next != null)
        {
            extractor.setResource(Constant.RESOURCE_WOOD, next);
        }
        else
        {
            extractor.stopExtraction();
        }
    }

    /**
     * Switch action between extract or carry when needed.
     */
    private void switchActionExtractCarry()
    {
        if (player.owns(this))
        {
            for (final Actionable actionable : hud.getActive())
            {
                final boolean carry = carryResource != null;
                Util.switchExtractCarryAction(actionable, carry);
            }
        }
    }

    @Override
    public void recycle()
    {
        attacker.stopAttack();
        pathfindable.stopMoves();
        extractor.stopExtraction();
        collidable.setEnabled(true);
        selectable.onSelection(false);
        resetFlags();
        carryResource = null;
        visible = true;
        display = true;
        stateHandler.changeState(StateIdle.class);
    }
}

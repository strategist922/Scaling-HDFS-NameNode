/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.hadoop.hdfs.server.blockmanagement;

import java.io.IOException;

import org.apache.hadoop.hdfs.protocol.Block;
import org.apache.hadoop.hdfs.server.common.HdfsServerConstants.BlockUCState;
import org.apache.hadoop.hdfs.server.namenode.BlocksHelper;
import org.apache.hadoop.hdfs.server.namenode.DBConnector;
import org.apache.hadoop.hdfs.server.namenode.INodeFile;
import org.apache.hadoop.hdfs.server.namenode.KthFsHelper;
import org.apache.hadoop.hdfs.util.LightWeightGSet;

/**
 * Internal class for block metadata.
 */
public class BlockInfo extends Block implements LightWeightGSet.LinkedElement {
	private INodeFile inode;

	/** For implementing {@link LightWeightGSet.LinkedElement} interface */
	private LightWeightGSet.LinkedElement nextLinkedElement;

	/**
	 * This array contains triplets of references.
	 * For each i-th datanode the block belongs to
	 * triplets[3*i] is the reference to the DatanodeDescriptor
	 * and triplets[3*i+1] and triplets[3*i+2] are references 
	 * to the previous and the next blocks, respectively, in the 
	 * list of blocks belonging to this data-node.
	 */

	/*
	 * [DatanodeDescriptor] [previous_block] [next_block] [DatanodeDescriptor] [previous_block] [next_block] [DatanodeDescriptor] [previous_block] [next_block]
	 * 
	 * */
	private Object[] triplets;
	private Object[] tripletsKTH; //[DD] [prevBlockID] [nextBlockID]

	private int blockIndex = -1; //added for KTHFS

	/**
	 * Construct an entry for blocksmap
	 * @param replication the block's replication factor
	 */
	public BlockInfo(int replication) {
		this.triplets = new Object[3*replication];
		this.inode = null;
	}

	public BlockInfo(Block blk, int replication) {
		super(blk);
		this.triplets = new Object[3*replication];
		this.inode = null;

		this.getBlockId(); 
		this.getBlockName();
		this.getNumBytes(); 
		this.getGenerationStamp();
	}

	/**
	 * Copy construction.
	 * This is used to convert BlockInfoUnderConstruction
	 * @param from BlockInfo to copy from.
	 */
	protected BlockInfo(BlockInfo from) {
		this(from, from.inode.getReplication());
		this.inode = from.inode;
	}

	public INodeFile getINode() {
		return inode;
	}

	/*public void setINode_old(INodeFile inode) {
    this.inode = inode;
  }*/
  
  public void setINode(INodeFile inode) {
	  this.inode = inode;
	  if(inode!=null)
	    BlocksHelper.updateINodeID(inode.getID(), this);
	  }

  public DatanodeDescriptor getDatanode(int index) {
	  System.err.println("getDatanode invoked with index: " + index);
    //assert this.triplets != null : "BlockInfo is not initialized";
    assert index >= 0 && index*3 < BlocksHelper.getTripletsForBlock(this).length : "Index is out of bound";
    //DatanodeDescriptor node = (DatanodeDescriptor)triplets[index*3];
    DatanodeDescriptor node = BlocksHelper.getDatanode(this.getBlockId(), index);
    assert node == null || 
        DatanodeDescriptor.class.getName().equals(node.getClass().getName()) : 
              "DatanodeDescriptor is expected at " + index*3;
    return node;
  }

  BlockInfo getPrevious(int index) {
    //assert this.triplets != null : "BlockInfo is not initialized";
    assert index >= 0 && index*3+1 < BlocksHelper.getTripletsForBlock(this).length : "Index is out of bound";
    //BlockInfo info = (BlockInfo)triplets[index*3+1];
    BlockInfo info = null;
	try {
		info = BlocksHelper.getNextPrevious(this.getBlockId(), index, false);
	} catch (IOException e) {
		// TODO Auto-generated catch block
		e.printStackTrace();
	}

    assert info == null || 
        info.getClass().getName().startsWith(BlockInfo.class.getName()) : 
              "BlockInfo is expected at " + index*3;
    return info;
  }

  BlockInfo getNext(int index) {
    assert this.triplets != null : "BlockInfo is not initialized";
    assert index >= 0 && index*3+2 < BlocksHelper.getTripletsForBlock(this).length : "Index is out of bound";
//    BlockInfo info = (BlockInfo)triplets[index*3+2];
    BlockInfo info = null;
  	try {
  		info = BlocksHelper.getNextPrevious(this.getBlockId(), index, true);
  	} catch (IOException e) {
  		// TODO Auto-generated catch block
  		e.printStackTrace();
  	}

    assert info == null || 
        info.getClass().getName().startsWith(BlockInfo.class.getName()) : 
              "BlockInfo is expected at " + index*3;
    return info;
  }

  void setDatanode(int index, DatanodeDescriptor node) {
    //assert this.triplets != null : "BlockInfo is not initialized";
    assert index >= 0 && index*3 < BlocksHelper.getTripletsForBlock(this).length : "Index is out of bound";
    //triplets[index*3] = node;
    if(node != null)
    	BlocksHelper.setDatanode(this.getBlockId(), index, node.name);
  }

  void setPrevious(int index, BlockInfo to) {
    //assert this.triplets != null : "BlockInfo is not initialized";
    assert index >= 0 && index*3+1 < BlocksHelper.getTripletsForBlock(this).length : "Index is out of bound";
    //triplets[index*3+1] = to;
    if(to != null)
    	BlocksHelper.setNextPrevious(this.getBlockId(), index, to, false);
    	
  }

  void setNext(int index, BlockInfo to) {
    //assert this.triplets != null : "BlockInfo is not initialized";
    assert index >= 0 && index*3+2 < BlocksHelper.getTripletsForBlock(this).length : "Index is out of bound";
    //triplets[index*3+2] = to;
    if(to != null)
    	BlocksHelper.setNextPrevious( this.getBlockId(), index, to, true);
  }

  int getCapacity() {
    //assert this.triplets != null : "BlockInfo is not initialized";
    assert BlocksHelper.getTripletsForBlock(this).length % 3 == 0 : "Malformed BlockInfo";
    return BlocksHelper.getTripletsForBlock(this).length / 3;
  }

  /**
   * Ensure that there is enough  space to include num more triplets.
   * @return first free triplet index.
   */
  private int ensureCapacity(int num) {
    //assert this.triplets != null : "BlockInfo is not initialized";
    int last = numNodes();
    Object [] temptriplets = BlocksHelper.getTripletsForBlock(this);
    if(temptriplets.length >= (last+num)*3)
      return last;
    /* Not enough space left. Create a new array. Should normally 
     * happen only when replication is manually increased by the user. */
    Object[] old = triplets;
    triplets = new Object[(last+num)*3];
    for(int i=0; i < last*3; i++) {
      triplets[i] = old[i];
    }
    return last;
  }

  /**
   * Count the number of data-nodes the block belongs to.
   */
  int numNodes() {
    assert this.triplets != null : "BlockInfo is not initialized";
    assert BlocksHelper.getTripletsForBlock(this).length % 3 == 0 : "Malformed BlockInfo";
    for(int idx = getCapacity()-1; idx >= 0; idx--) {
      if(getDatanode(idx) != null)
        return idx+1;
    }
    return 0;
  }

  /**
   * Add data-node this block belongs to.
   */
  public boolean addNode(DatanodeDescriptor node) {
    if(findDatanode(node) >= 0) // the node is already there
      return false;

    // find the last null node
    int lastNode = ensureCapacity(1);
    setDatanode(lastNode, node);
    setNext(lastNode, null);
    setPrevious(lastNode, null);
    return true;
  }

  /**
   * Remove data-node from the block.
   */
  public boolean removeNode(DatanodeDescriptor node) {
    int dnIndex = findDatanode(node);
    if(dnIndex < 0) // the node is not found
      return false;
    assert getPrevious(dnIndex) == null && getNext(dnIndex) == null : 
      "Block is still in the list and must be removed first.";
    // find the last not null node
    int lastNode = numNodes()-1; 
    // replace current node triplet by the lastNode one 
    setDatanode(dnIndex, getDatanode(lastNode));
    setNext(dnIndex, getNext(lastNode)); 
    setPrevious(dnIndex, getPrevious(lastNode)); 
    // set the last triplet to null
    setDatanode(lastNode, null);
    setNext(lastNode, null); 
    setPrevious(lastNode, null); 
    return true;
  }

  /**
   * Find specified DatanodeDescriptor.
   * @param dn
   * @return index or -1 if not found.
   */
  int findDatanode(DatanodeDescriptor dn) {
    int len = getCapacity();
    System.err.println("Capacity: " + len);
    for(int idx = 0; idx < len; idx++) {
      DatanodeDescriptor cur = getDatanode(idx);
      if(cur == dn){
        return idx;
      }
      if(cur == null){
        break;
      }
    }
    return -1;
  }

  /**
   * Insert this block into the head of the list of blocks 
   * related to the specified DatanodeDescriptor.
   * If the head is null then form a new list.
   * @return current block as the new head of the list.
   */
  public BlockInfo listInsert(BlockInfo head, DatanodeDescriptor dn) {
    int dnIndex = this.findDatanode(dn);
    assert dnIndex >= 0 : "Data node is not found: current";
    assert getPrevious(dnIndex) == null && getNext(dnIndex) == null : 
            "Block is already in the list and cannot be inserted.";
    this.setPrevious(dnIndex, null);
    this.setNext(dnIndex, head);
    if(head != null)
      head.setPrevious(head.findDatanode(dn), this);
    return this;
  }

  /**
   * Remove this block from the list of blocks 
   * related to the specified DatanodeDescriptor.
   * If this block is the head of the list then return the next block as 
   * the new head.
   * @return the new head of the list or null if the list becomes
   * empty after deletion.
   */
  public BlockInfo listRemove(BlockInfo head, DatanodeDescriptor dn) {
    if(head == null)
      return null;
    int dnIndex = this.findDatanode(dn);
    if(dnIndex < 0) // this block is not on the data-node list
      return head;

    BlockInfo next = this.getNext(dnIndex);
    BlockInfo prev = this.getPrevious(dnIndex);
    
    this.setNext(dnIndex, null);
    this.setPrevious(dnIndex, null);
    
    
    if(prev != null)
      prev.setNext(prev.findDatanode(dn), next);
    if(next != null)
      next.setPrevious(next.findDatanode(dn), prev);
    if(this == head)  // removing the head
      head = next;
    BlocksHelper.removeTriplets(this,dnIndex);
    System.out.println("printed once !!!!!!");
    return head;
  }

  /**
   * BlockInfo represents a block that is not being constructed.
   * In order to start modifying the block, the BlockInfo should be converted
   * to {@link BlockInfoUnderConstruction}.
   * @return {@link BlockUCState#COMPLETE}
   */
  public BlockUCState getBlockUCState() {
    return BlockUCState.COMPLETE;
  }

  /**
   * Is this block complete?
   * 
   * @return true if the state of the block is {@link BlockUCState#COMPLETE}
   */
  public boolean isComplete() {
    return getBlockUCState().equals(BlockUCState.COMPLETE);
  }

  /**
   * Convert a complete block to an under construction block.
   * 
   * @return BlockInfoUnderConstruction -  an under construction block.
   */
  public BlockInfoUnderConstruction convertToBlockUnderConstruction(
      BlockUCState s, DatanodeDescriptor[] targets) {
    if(isComplete()) {
      return new BlockInfoUnderConstruction(
          this, getINode().getReplication(), s, targets);
    }
    // the block is already under construction
    BlockInfoUnderConstruction ucBlock = (BlockInfoUnderConstruction)this;
    ucBlock.setBlockUCState(s);
    ucBlock.setExpectedLocations(targets);
    return ucBlock;
  }

  @Override
  public int hashCode() {
    // Super implementation is sufficient
    return super.hashCode();
  }
  
  @Override
  public boolean equals(Object obj) {
    // Sufficient to rely on super's implementation
    return (this == obj) || super.equals(obj);
  }

  @Override
  public LightWeightGSet.LinkedElement getNext() {
    return nextLinkedElement;
  }

  @Override
  public void setNext(LightWeightGSet.LinkedElement next) {
    this.nextLinkedElement = next;
  }
  /*added for KTHFS*/
  public void setTripletsKTH(Object[] trips) {
	  this.triplets = trips;
  }
  /*added for KTHFS*/
  public Object[] getTripletsKTH() {
	  return this.triplets;
  }
  
  /*added for KTHFS*/
  public int getBlockIndex() {
	  return this.blockIndex;
  }
  /*added for KTHFS*/
  public void setBlockIndex(int bindex) {
	  this.blockIndex = bindex;
  }
  
}
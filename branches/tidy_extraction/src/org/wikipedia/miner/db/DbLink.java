package org.wikipedia.miner.db;

public class DbLink  implements Comparable<DbLink>{

	int id ;
	short[] positionOffsets ;
	
	//int[] positions ;
	
	public DbLink(int id, int[]positions) {
		this.id = id ;
		
		if (positions != null) {
			this.positionOffsets = new short[positions.length] ;
			
			for (int i=0 ; i<positions.length ; i++) {
				
				if (i==0) {
					positionOffsets[i] = (short) positions[i] ;
				} else {
					positionOffsets[i] = (short) (positions[i] - positions[i-1]) ;
				}
			}
		}

	}
	
	public int getId() {
		return id ;
	}
	
	public int[] getPositions() {
		
		if (positionOffsets == null)
			return null ;
		
		int[] positions = new int[positionOffsets.length] ;
		
		int dist = 0 ;
		for (int i=0 ; i<positionOffsets.length ; i++) {
			dist = dist + positionOffsets[i] ;
			positions[i] = dist ;
		}
		
		
		return positions ;
	}
	
	public int compareTo(DbLink link) {
		return new Integer(id).compareTo(link.id) ;
	}
	
}

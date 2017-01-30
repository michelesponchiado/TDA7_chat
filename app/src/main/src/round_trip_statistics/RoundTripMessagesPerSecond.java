package round_trip_statistics;
	/**
	 * this class implements some statistics useful for the diagnostic message thread
	 * @author root
	 *
	 */
	public class RoundTripMessagesPerSecond{
		boolean b_new_value_available;
		long l_base_time_ms;
		long l_num_msgs_rx;
		long l_num_msgs_rx_total;
		final int upd_stats_time_ms=2000;
		final int size_average=16;
		double dbltime_avg[];
		double dblnummsg_avg[];
		int idx_avg;
		int num_avg;
		double dbl_max_msg_per_s;
		double dbl_avg_msg_per_s;
		double dbl_last_msg_per_s;
		/**
		 * clear the statistics
		 */
		public void clear(){
			for (int i=0;i<dbltime_avg.length;i++){
				dbltime_avg[i]=0.0;
			}
			for (int i=0;i<dblnummsg_avg.length;i++){
				dblnummsg_avg[i]=0.0;
			}
			l_base_time_ms=0;
			l_num_msgs_rx=0;
			l_num_msgs_rx_total=0;
			dbl_max_msg_per_s=0;
			dbl_avg_msg_per_s=0;
			dbl_last_msg_per_s=0;
			idx_avg=0;
			num_avg=0;
			b_new_value_available=false;
		}
		/**
		 * build us the object
		 */
		public RoundTripMessagesPerSecond(){
			dbltime_avg=new double[size_average];
			dblnummsg_avg=new double[size_average];
			this.clear();
		}
		/**
		 * starts the statistics
		 */
		public void start(){
			l_base_time_ms=System.currentTimeMillis();
			l_num_msgs_rx=0;
		}
		/**
		 * 
		 * @return the average speed in messages per second
		 */
		public double get_average_speed(){
			return dbl_avg_msg_per_s;
		}
		/**
		 * 
		 * @return he maximum transfer speed reached
		 */
		public double get_max_speed(){
			return dbl_max_msg_per_s;
		}
		/**
		 * 
		 * @return the actual transfer speed
		 */
		public double get_act_speed(){
			return dbl_last_msg_per_s;
		}
		/**
		 * 
		 * @return the total number of messages received
		 */
		public double get_num_msg_rx_total(){
			return l_num_msgs_rx_total;
		}
		/**
		 * 
		 * @return true if a new update of the statistics is available
		 */
		public boolean new_value_avail(){
			boolean b;
			b=b_new_value_available;
			b_new_value_available=false;
			return b;
		}
		/**
		 * update the statistics
		 * @param i_num_msg_received the number of messages receive from the last update
		 */
		public void update(int i_num_msg_received){
			long l_act_time_ms;
			l_num_msgs_rx+=i_num_msg_received;
			l_num_msgs_rx_total+=i_num_msg_received;
			l_act_time_ms=System.currentTimeMillis();
			if (l_act_time_ms<l_base_time_ms){
				l_base_time_ms=l_act_time_ms;
			}
			else{
				long l_elapsed_time_ms;
				l_elapsed_time_ms=l_act_time_ms-l_base_time_ms;
				if (l_elapsed_time_ms>=upd_stats_time_ms){
					dbl_last_msg_per_s=(l_num_msgs_rx*1000.0)/l_elapsed_time_ms;
					if (dbl_last_msg_per_s>dbl_max_msg_per_s){
						dbl_max_msg_per_s=dbl_last_msg_per_s;
					}
					dbltime_avg[idx_avg]=l_elapsed_time_ms;
					dblnummsg_avg[idx_avg]=l_num_msgs_rx;
					if (++idx_avg>=dbltime_avg.length){
						idx_avg=0;
					}
					if (num_avg<dbltime_avg.length){
						num_avg++;
					}
					double dbl_tot_time_avg;
					double dbl_tot_msg_avg;
					dbl_tot_time_avg=0;
					dbl_tot_msg_avg=0;
					for (int i=0;i<num_avg;i++){
						dbl_tot_time_avg+=dbltime_avg[i];
						dbl_tot_msg_avg+=dblnummsg_avg[i];
					}
					dbl_avg_msg_per_s=dbl_tot_msg_avg*1000.0/dbl_tot_time_avg;
					this.start();
					b_new_value_available=true;
					
				}
			}
		}
	};

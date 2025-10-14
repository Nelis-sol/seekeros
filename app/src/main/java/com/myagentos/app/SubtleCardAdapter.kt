package com.myagentos.app

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

data class SubtleCard(
    val title: String,
    val subtitle: String,
    val category: String,
    val description: String,
    val imageRes: Int,
    val actionButtonText: String,
    val learnMoreAction: () -> Unit,
    val actionButtonAction: () -> Unit
)

class SubtleCardAdapter(
    private val cards: List<SubtleCard>
) : RecyclerView.Adapter<SubtleCardAdapter.CardViewHolder>() {

    class CardViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val cardImage: ImageView = itemView.findViewById(R.id.cardImage)
        val cardTitle: TextView = itemView.findViewById(R.id.cardTitle)
        val cardSubtitle: TextView = itemView.findViewById(R.id.cardSubtitle)
        val cardCategory: TextView = itemView.findViewById(R.id.cardCategory)
        val cardDescription: TextView = itemView.findViewById(R.id.cardDescription)
        val actionButton: Button = itemView.findViewById(R.id.actionButton)
        val learnMoreButton: Button = itemView.findViewById(R.id.learnMoreButton)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CardViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_subtle_card, parent, false)
        return CardViewHolder(view)
    }

    override fun onBindViewHolder(holder: CardViewHolder, position: Int) {
        val card = cards[position]
        
        holder.cardTitle.text = card.title
        holder.cardSubtitle.text = card.subtitle
        holder.cardCategory.text = card.category
        holder.cardDescription.text = card.description
        holder.cardImage.setImageResource(card.imageRes)
        holder.actionButton.text = card.actionButtonText
        
        holder.learnMoreButton.setOnClickListener {
            card.learnMoreAction()
        }
        
        holder.actionButton.setOnClickListener {
            card.actionButtonAction()
        }
    }

    override fun getItemCount(): Int = cards.size
}

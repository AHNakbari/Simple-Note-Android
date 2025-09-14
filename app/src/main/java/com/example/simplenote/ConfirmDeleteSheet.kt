package com.example.simplenote

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.setFragmentResult
import androidx.navigation.fragment.findNavController
import com.example.simplenote.databinding.DialogDeleteNoteBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class ConfirmDeleteSheet : BottomSheetDialogFragment() {


    private var binding: DialogDeleteNoteBinding? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = DialogDeleteNoteBinding.inflate(inflater, container, false)
        return binding?.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding?.btnClose?.setOnClickListener {
            findNavController().navigateUp()
        }

        binding?.btnDeleteNote?.setOnClickListener {
            setFragmentResult(DELETE_RESULT, bundleOf())
        }
    }

    companion object {
        const val DELETE_RESULT = "DeleteBottomSheetResult"

    }

}
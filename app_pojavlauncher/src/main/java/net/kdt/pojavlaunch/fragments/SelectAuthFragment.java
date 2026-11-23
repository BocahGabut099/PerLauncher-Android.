package net.kdt.pojavlaunch.fragments;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;

public class SelectAuthFragment extends Fragment {
    public static final String TAG = "AUTH_SELECT_FRAGMENT";

    public SelectAuthFragment() {
        super(R.layout.fragment_select_auth_method);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        Button microsoft = view.findViewById(R.id.button_microsoft_authentication);
        Button elyBy = view.findViewById(R.id.button_elyby_authentication);
        Button offline = view.findViewById(R.id.button_local_authentication);

        microsoft.setOnClickListener(v -> Tools.swapFragment(requireActivity(),
                MicrosoftLoginFragment.class, MicrosoftLoginFragment.TAG, null));
        elyBy.setOnClickListener(v -> Tools.swapFragment(requireActivity(),
                ElyByLoginFragment.class, ElyByLoginFragment.TAG, null));
        offline.setOnClickListener(v -> Tools.swapFragment(requireActivity(),
                LocalLoginFragment.class, LocalLoginFragment.TAG, null));
    }
}
